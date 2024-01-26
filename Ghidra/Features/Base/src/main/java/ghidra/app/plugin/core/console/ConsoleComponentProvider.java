/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.console;

import java.awt.*;
import java.awt.event.*;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;

import org.apache.commons.lang3.StringUtils;

import docking.*;
import docking.action.*;
import docking.widgets.CursorPosition;
import docking.widgets.FindDialog;
import docking.widgets.FindDialogSearcher;
import docking.widgets.SearchLocation;
import generic.theme.GIcon;
import generic.theme.Gui;
import generic.util.WindowUtilities;
import ghidra.app.plugin.core.interpreter.PrimitiveFindTextDialog;
import ghidra.app.services.*;
import ghidra.framework.main.ConsoleTextPane;
import ghidra.framework.options.ToolOptions;
import ghidra.framework.plugintool.ComponentProviderAdapter;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Program;
import ghidra.program.model.symbol.SymbolIterator;
import ghidra.program.model.symbol.SymbolTable;
import ghidra.util.HelpLocation;
import ghidra.util.Msg;

public class ConsoleComponentProvider extends ComponentProviderAdapter
		implements ConsoleService {

	private static final String OLD_NAME = "ConsolePlugin";
	private static final String NAME = "Console";

	private static final String DEFAULT_FONT_ID = "font.plugin.console";
	private static final String FONT_OPTION_LABEL = "Font";
	private static final String FONT_DESCRIPTION =
		"This is the font that will be used in the Console.  " +
			"Double-click the font example to change it.";

	private ConsoleTextPane textPane;
	private JScrollPane scroller;
	private JComponent component;
	private boolean scrollLock = false;
	private DockingAction clearAction;
	private ToggleDockingAction scrollAction;
	private Address currentAddress;
	private PrintWriter stderr;
	private PrintWriter stdin;
	private Program currentProgram;

	public ConsoleComponentProvider(PluginTool tool, String owner) {
		super(tool, "Console", owner);

		// note: the owner has not changed, just the name; remove sometime after version 10
		ComponentProvider.registerProviderNameOwnerChange(OLD_NAME, owner, NAME, owner);

		setDefaultWindowPosition(WindowPosition.BOTTOM);
		setHelpLocation(new HelpLocation(owner, owner));
		setIcon(new GIcon("icon.plugin.console.provider"));
		setWindowMenuGroup("Console");
		setSubTitle("Scripting");
		setTitle("Console");
		createOptions();
		build();
		createActions();
	}

	@Override
	public boolean canBeParent() {
		// the console window may appear and be closed often and thus is not a suitable parent when
		// in a window by itself
		return false;
	}

	void init() {
		stderr = new PrintWriter(new ConsoleWriter(this, true));
		stdin = new PrintWriter(new ConsoleWriter(this, false));

		/* call this before build() -- we get our Font here */
		setVisible(true);
	}

	void dispose() {
		textPane.dispose();
		stderr.close();
		stdin.close();
	}

	private void createOptions() {
		ToolOptions options = tool.getOptions("Console");
		HelpLocation help = new HelpLocation(getOwner(), getOwner());
		options.registerThemeFontBinding(FONT_OPTION_LABEL, DEFAULT_FONT_ID, help,
			FONT_DESCRIPTION);
		options.setOptionsHelpLocation(help);
	}

	private void build() {

		textPane = new ConsoleTextPane(tool);
		textPane.setName("CONSOLE");
		Gui.registerFont(textPane, DEFAULT_FONT_ID);
		textPane.setEditable(false);
		textPane.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				if (currentProgram == null) {
					return;
				}

				Point hoverPoint = e.getPoint();
				ConsoleWord word = getWordSeparatedByWhitespace(hoverPoint);
				if (word == null) {
					textPane.setCursor(Cursor.getDefaultCursor());
					return;
				}

				Address addr = currentProgram.getAddressFactory().getAddress(word.word);
				if (addr != null || isSymbol(word.word)) {
					textPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
					return;
				}

				ConsoleWord trimmedWord = word.getWordWithoutSpecialCharacters();
				addr = currentProgram.getAddressFactory().getAddress(trimmedWord.word);
				if (addr != null || isSymbol(trimmedWord.word)) {
					textPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
					return;
				}
			}
		});
		textPane.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (e.getClickCount() != 2) {
					return;
				}
				if (currentProgram == null) {
					return;
				}

				GoToService gotoService = tool.getService(GoToService.class);
				if (gotoService == null) {
					return;
				}

				Point clickPoint = e.getPoint();
				ConsoleWord word = getWordSeparatedByWhitespace(clickPoint);
				if (word == null) {
					return;
				}

				Address addr = currentProgram.getAddressFactory().getAddress(word.word);
				if (addr != null || isSymbol(word.word)) {
					goTo(word);
					return;
				}

				ConsoleWord trimmedWord = word.getWordWithoutSpecialCharacters();
				addr = currentProgram.getAddressFactory().getAddress(trimmedWord.word);
				if (addr == null && !isSymbol(trimmedWord.word)) {
					return;
				}

				goTo(trimmedWord);
			}
		});

		scroller = new JScrollPane(textPane);
		scroller.setPreferredSize(new Dimension(200, 100));

		component = new JPanel(new BorderLayout(5, 5));
		component.add(scroller, BorderLayout.CENTER);

		tool.addComponentProvider(this, true);
	}

	private void goTo(ConsoleWord word) {

		GoToService gotoService = tool.getService(GoToService.class);
		if (gotoService == null) {
			return;
		}

		// NOTE: must be case sensitive otherwise the service will report that it has
		//       processed the request even if there are no matches
		boolean found =
			gotoService.goToQuery(currentAddress, new QueryData(word.word, true), null, null);
		if (found) {
			select(word);
			return;
		}

		ConsoleWord trimmedWord = word.getWordWithoutSpecialCharacters();
		found = gotoService.goToQuery(currentAddress, new QueryData(trimmedWord.word, true), null,
			null);
		if (found) {
			select(trimmedWord);
		}
	}

	private ConsoleWord getWordSeparatedByWhitespace(Point p) {
		int pos = textPane.viewToModel2D(p);
		Document doc = textPane.getDocument();
		int startIndex = pos;
		int endIndex = pos;
		try {
			for (; startIndex > 0; --startIndex) {
				char c = doc.getText(startIndex, 1).charAt(0);
				if (Character.isWhitespace(c)) {
					break;
				}
			}
			for (; endIndex < doc.getLength() - 1; ++endIndex) {
				char c = doc.getText(endIndex, 1).charAt(0);
				if (Character.isWhitespace(c)) {
					break;
				}
			}
			String text = doc.getText(startIndex + 1, endIndex - startIndex);
			if (text == null || text.trim().length() == 0) {
				return null;
			}
			return new ConsoleWord(text.trim(), startIndex + 1, endIndex);
		}
		catch (BadLocationException ble) {
			return null;
		}
	}

	private boolean isSymbol(String word) {
		SymbolTable symbolTable = currentProgram.getSymbolTable();
		SymbolIterator symbolIterator = symbolTable.getSymbols(word);
		return symbolIterator.hasNext();
	}

	protected void select(ConsoleWord word) {
		try {
			textPane.select(word.startPosition, word.endPosition);
		}
		catch (Exception e) {
			// we are too lazy to verify our data before calling select--bleh
		}
	}

	private void createActions() {
		clearAction = new DockingAction("Clear Console", getOwner()) {

			@Override
			public void actionPerformed(ActionContext context) {
				clearMessages();
			}
		};
		clearAction.setDescription("Clear Console");
// ACTIONS - auto generated
		clearAction.setToolBarData(new ToolBarData(new GIcon("icon.plugin.console.clear"), null));

		clearAction.setEnabled(true);

		scrollAction = new ToggleDockingAction("Scroll Lock", getOwner()) {
			@Override
			public void actionPerformed(ActionContext context) {
				textPane.setScrollLock(isSelected());
			}
		};
		scrollAction.setDescription("Scroll Lock");
		scrollAction
				.setToolBarData(
					new ToolBarData(new GIcon("icon.plugin.console.scroll.lock"), null));

		scrollAction.setEnabled(true);
		scrollAction.setSelected(scrollLock);

		addLocalAction(scrollAction);
		addLocalAction(clearAction);
		addLocalAction(new FindAction());
	}

	@Override
	public void addMessage(String originator, String message) {
		checkVisible();
		textPane.addMessage(originator + "> " + message + "\n");
	}

	@Override
	public void addErrorMessage(String originator, String message) {
		checkVisible();
		textPane.addErrorMessage(originator + "> " + message + "\n");
	}

	@Override
	public void addException(String originator, Exception e) {
		try {
			e.printStackTrace(stderr);
		}
		catch (Exception e1) {
			//
			// sometimes an exception will occur while printing
			// the stack trace on an exception.
			// if that happens catch it and manually print
			// some information about it.
			// see org.jruby.exceptions.RaiseException
			//
			stderr.println("Unexpected Exception: " + e.getMessage());
			for (StackTraceElement stackTraceElement : e.getStackTrace()) {
				stderr.println("\t" + stackTraceElement.toString());
			}
			stderr.println("Unexpected Exception: " + e1.getMessage());
			for (StackTraceElement stackTraceElement : e1.getStackTrace()) {
				stderr.println("\t" + stackTraceElement.toString());
			}
		}
		Msg.error(this, "Unexpected Exception: " + e.getMessage(), e);
	}

	@Override
	public void clearMessages() {
		checkVisible();
		textPane.setText("");
	}

	@Override
	public void print(String msg) {
		checkVisible();
		textPane.addPartialMessage(msg);
	}

	@Override
	public void printError(String errmsg) {
		checkVisible();
		textPane.addErrorMessage(errmsg);
	}

	@Override
	public void println(String msg) {
		checkVisible();
		textPane.addMessage(msg + "\n");
	}

	@Override
	public void printlnError(String errmsg) {
		checkVisible();
		textPane.addErrorMessage(errmsg + "\n");
	}

	@Override
	public PrintWriter getStdErr() {
		return stderr;
	}

	@Override
	public PrintWriter getStdOut() {
		return stdin;
	}

	@Override
	public String getText(int offset, int length) {
		try {
			return textPane.getDocument().getText(offset, length);
		}
		catch (BadLocationException e) {
			// handled below
		}
		return null;
	}

	@Override
	public int getTextLength() {
		return textPane.getDocument().getLength();
	}

	////////////////////////////////////////////////////////////////////

	private void checkVisible() {
		if (!isVisible()) {
			tool.showComponentProvider(this, true);
		}
	}

	/**
	 * @see docking.ComponentProvider#getComponent()
	 */
	@Override
	public JComponent getComponent() {
		return component;
	}

	@Override
	public void requestFocus() {
		KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
		Component focusOwner = kfm.getFocusOwner();
		if (focusOwner != null) {
			if (SwingUtilities.isDescendingFrom(focusOwner, component)) {
				return; // nothing to do
			}
		}

		component.requestFocus();
	}

	public void setCurrentProgram(Program program) {
		currentProgram = program;
	}

	public void setCurrentAddress(Address address) {
		currentAddress = address;
	}
	
	
	 
	class FindAction extends DockingAction {
		PrimitiveFindTextDialog primitiveFindTextDialog;

		public FindAction() {
			super("Find", ConsoleComponentProvider.this.getName());
			setPopupMenuData(new MenuData(new String[] { "&Find..." }));
			setKeyBindingData(new KeyBindingData(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
			setEnabled(true);

			primitiveFindTextDialog = new PrimitiveFindTextDialog(textPane, "Console Find Text");
		}

		@Override
		public void dispose() {
			primitiveFindTextDialog.dispose();
			super.dispose();
		}

		@Override
		public void actionPerformed(ActionContext context) {
			primitiveFindTextDialog.showDialog();
		}
	}
	
}
