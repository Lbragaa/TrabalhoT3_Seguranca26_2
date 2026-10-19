/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.ui;

import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

final class FieldLimits {
    private FieldLimits() { }
    static void apply(JTextComponent field, int maximum) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override public void insertString(FilterBypass b, int offset, String text, AttributeSet attributes) throws BadLocationException {
                replace(b, offset, 0, text, attributes);
            }
            @Override public void replace(FilterBypass b, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
                if (b.getDocument().getLength() - length + (text == null ? 0 : text.length()) <= maximum) { b.replace(offset, length, text, attributes); }
            }
        });
    }
}
