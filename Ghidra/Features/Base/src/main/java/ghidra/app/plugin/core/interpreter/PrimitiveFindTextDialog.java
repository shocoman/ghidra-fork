package ghidra.app.plugin.core.interpreter;

import java.awt.Color;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Painter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.Highlighter.Highlight;
import javax.swing.text.Highlighter.HighlightPainter;
import javax.swing.text.JTextComponent;

import org.apache.commons.lang3.StringUtils;

import docking.DockingWindowManager;
import docking.event.mouse.GMouseListenerAdapter;
import docking.widgets.CursorPosition;
import docking.widgets.FindDialog;
import docking.widgets.FindDialogSearcher;
import docking.widgets.SearchLocation;
import generic.theme.GColor;
import generic.theme.GThemeDefaults.Colors.Palette;
import generic.util.WindowUtilities;

public class PrimitiveFindTextDialog {
	// system.color.fg.selected.view
	// system.color.bg.selected.view

	private FindDialog findDialog;
	private Object selectionHighlightTag = null;
	private String dialogName;
	private JTextComponent textPane;
	
	boolean searchFromUserCaret = false;
	
	int lastSearchPos = -1;
	int searchCaretPos = -1;
	
	class MyHighlightPainter extends DefaultHighlightPainter {
		public MyHighlightPainter(Color c) {
			super(c);
		}
	}
	
	public PrimitiveFindTextDialog(JTextComponent pane, String name) {
		dialogName = name;
		textPane = pane;

		textPane.getDocument().addDocumentListener(new DocumentListener() {
			
			@Override
			public void removeUpdate(DocumentEvent e) {
//				System.out.printf("removeUpdate: %s, %s\n", e.getOffset(), e.getLength());
				textUpdate(e.getOffset(), e.getLength(), false);
			}
			
			@Override
			public void insertUpdate(DocumentEvent e) {
//				System.out.printf("insertUpdate: %s, %s\n", e.getOffset(), e.getLength());
				textUpdate(e.getOffset(), e.getLength(), true);
			}
			
			@Override
			public void changedUpdate(DocumentEvent e) {
				System.out.printf("changedUpdate: %s\n", e.toString());
			}
		});
		
		textPane.addMouseListener(new GMouseListenerAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON1) {
					System.out.printf("Click: %s , %s \n", e.getButton(), textPane.viewToModel2D(e.getPoint()));
					searchFromUserCaret = true;
				}
			}
		});
	}
	
	private void textUpdate(int changePos, int changeLen, boolean isInsert) {
		var currentHighlight = getCurrentHighlight();
		if (currentHighlight == null) {
			return;
		}
		
		int highlightPos = currentHighlight.getStartOffset();
		int highlightLen = currentHighlight.getEndOffset() - currentHighlight.getStartOffset();

		System.out.printf("Change: (%s, %s); Highlight: (%s, %s)\n", changePos, changeLen, highlightPos, highlightLen);
		
		if (isInsert) {
			if (changePos > highlightPos && changePos <= highlightPos + highlightLen) {
				removeHighlight();
			} else if (changePos <= highlightPos) {
				lastSearchPos += changeLen;
				searchCaretPos += changeLen;
			}
		} else {
			if (changePos + changeLen > highlightPos && changePos < highlightPos + highlightLen) {
				removeHighlight();
			} else if (changePos < highlightPos) {
				lastSearchPos -= changeLen;
				searchCaretPos -= changeLen;
			}
		}
	}
	
	public Highlight getCurrentHighlight() {
		Highlight[] highlights = textPane.getHighlighter().getHighlights();
		for (int i = 0; i < highlights.length; i++) {
			Highlight high = highlights[i];
			boolean isMine = high.getPainter() instanceof MyHighlightPainter;
//			System.out.printf(" - %d) %s, %s, (is mine: %s)\n", i, high.getStartOffset(), high.getEndOffset(), isMine);

			if (isMine) {
				return high;
			}
		}
		return null;
	}

	private FindDialog getFindDialog(JTextComponent pane) {
		if (findDialog == null) {
			findDialog = new FindDialog(dialogName, new PrimitiveSearcher()) {
				@Override
				public void close() {
					super.close();
					removeHighlight();
				}
			};
		}
		return findDialog;
	}

	public void showDialog() {
		FindDialog dialog = getFindDialog(textPane);
		String selectedText = textPane.getSelectedText();
		if (!StringUtils.isBlank(selectedText)) {
			dialog.setSearchText(selectedText);
		}

		Window parentWindow = WindowUtilities.windowForComponent(textPane);
		DockingWindowManager.showDialog(parentWindow, findDialog);
	}

	public void dispose() {
		if (findDialog != null) {
			findDialog.dispose();
			findDialog = null;
		}
	}

	public void hideDialog() {
		if (findDialog != null) {
			findDialog.close();
		}
		removeHighlight();
	}

	public void setHighlight(int pos, int length) {
		int start = pos;
		int end = start + length;
		removeHighlight();
		
//		var painter = DefaultHighlighter.DefaultPainter;
		var painter = new MyHighlightPainter(null);
		try {
			selectionHighlightTag = textPane.getHighlighter().addHighlight(start, end, painter);
		} catch (BadLocationException e) {
		}
	}


	public void removeHighlight() {
		if (selectionHighlightTag != null) {
			textPane.getHighlighter().removeHighlight(selectionHighlightTag);
//			lastSearchPos = -1;
			selectionHighlightTag = null;
		}
	}
	
	public void searchNext() {
		findDialog.next();
	}
	
	public void searchPrevious() {
		findDialog.previous();
	}
	
	private class PrimitiveSearcher implements FindDialogSearcher {


		public PrimitiveSearcher() {
		}

		@Override
		public CursorPosition getCursorPosition() {
//			if (searchFromUserCaret) {
//				searchFromUserCaret = false;
//				return new CursorPosition(textPane.getCaretPosition());	
//			}
			
//			var h = getCurrentHighlight();
//			if (h != null) {
//				return new CursorPosition(h.getStartOffset());	
//			} else 
			{
//				return new CursorPosition(textPane.getCaretPosition());
			}
//			if (searchCaretPos < 0) {
//				searchCaretPos = textPane.getCaretPosition();
//			}
			
			System.out.printf("\nloadCursor: %s\n", searchCaretPos);
			return new CursorPosition(searchCaretPos);
		}

		@Override
		public void setCursorPosition(CursorPosition position) {
//			textPane.setCaretPosition(position.getPosition());
			System.out.printf("setCursor: %s\n", position.getPosition());
			searchCaretPos = position.getPosition();
			
			// scroll view to the search cursor
			try {
				var rect = textPane.modelToView2D(searchCaretPos);
				textPane.scrollRectToVisible(rect.getBounds());	
			} catch (BadLocationException e) {
			}
		}

		@Override
		public CursorPosition getStart() {
			return new CursorPosition(0);
		}

		@Override
		public CursorPosition getEnd() {
			int length = textPane.getDocument().getLength();
			return new CursorPosition(length - 1);
		}

		@Override
		public void highlightSearchResults(SearchLocation location) {
			if (location == null) {
				return;
			}

			setHighlight(location.getStartIndexInclusive(), location.getMatchLength());
		}

		@Override
		public SearchLocation search(String text, CursorPosition cursorPosition, boolean searchForward,
				boolean useRegex) {
			

			long startTime = System.nanoTime();

			String screenText;
			try {
				Document document = textPane.getDocument();
				screenText = document.getText(0, document.getLength());
			} catch (BadLocationException e) {
				return null;
			}

			int searchStartPos = cursorPosition.getPosition();
			if (searchFromUserCaret) {
				searchFromUserCaret = false;
				searchStartPos = searchCaretPos = textPane.getCaretPosition();
			} else if (searchStartPos == lastSearchPos) 
			{
				searchStartPos = searchForward ? searchStartPos + 1 : searchStartPos - 1;
			}
			
			System.out.printf("search. CursorPos: %s; StartPos: %s; LastPos: %s\n", searchCaretPos, searchStartPos, lastSearchPos);
			

			if (useRegex) {
				Pattern pattern = Pattern.compile(text, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
				Matcher matcher = pattern.matcher(screenText);
				int start = -1;
				int end = -1;

				if (searchForward) {
					if (matcher.find(searchStartPos)) {
						start = matcher.start();
						end = matcher.end();
						lastSearchPos = start;
						System.out.printf("search. NewPos: %s\n", start);
						System.out.println("TimeElapsed: " + (System.nanoTime() - startTime));
						return new SearchLocation(start, end - 1, text, searchForward);
					}
				} else {
					// to search backwards, find all matches before the string and take the
					// rightmost one
					while (matcher.find() && matcher.start() <= searchStartPos) {
						start = matcher.start();
						end = matcher.end();
					}

					if (start != -1) {
						lastSearchPos = start;
						System.out.printf("search. NewPos: %s\n", start);
						System.out.println("TimeElapsed: " + (System.nanoTime() - startTime));
						return new SearchLocation(start, end - 1, text, searchForward);
					}
				}
			} else {
				int start = -1;
				if (searchForward) {
					start = StringUtils.indexOfIgnoreCase(screenText, text, searchStartPos);
				} else {
					start = StringUtils.lastIndexOfIgnoreCase(screenText, text, searchStartPos);
				}

				if (start != -1) {
					lastSearchPos = start;
					System.out.printf("search. NewPos: %s\n", start);
					System.out.println("TimeElapsed: " + (System.nanoTime() - startTime));
					return new SearchLocation(start, start + text.length() - 1, text, searchForward);
				}
			}

			System.out.printf("search. NewPos: %s\n", -1);
			System.out.println("TimeElapsed: " + (System.nanoTime() - startTime));
			
			lastSearchPos = -1;
			return null;
		}
	}

}
