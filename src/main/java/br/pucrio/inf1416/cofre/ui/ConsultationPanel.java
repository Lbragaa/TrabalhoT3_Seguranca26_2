/*
 * Integrantes:
 * Luan Braga - Matricula: 2411776
 * Diogo Benchimol - Matricula: 2312917
 */
package br.pucrio.inf1416.cofre.ui;

import br.pucrio.inf1416.cofre.db.UserRepository.User;
import br.pucrio.inf1416.cofre.vault.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public final class ConsultationPanel extends JPanel {
    final JTextField folder = new JTextField(35);
    final JPasswordField phrase = new JPasswordField(28);
    final JButton browse = new JButton("Selecionar pasta…"), list = new JButton("Listar"), extract = new JButton("Extrair arquivo selecionado…");
    final JButton back = new JButton("Voltar de Consultar para o Menu Principal");
    final JLabel count = new JLabel(), status = new JLabel("Informe a pasta segura e a frase secreta da sua chave privada.");
    final JTable table;
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Nome secreto", "Dono", "Grupo"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private List<Catalog.Entry> entries = List.of();
    private boolean busy;
    public ConsultationPanel(User user) {
        super(new BorderLayout(8, 12)); setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        FieldLimits.apply(folder, 255); FieldLimits.apply(phrase, 255);
        JPanel top = new JPanel(); top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(new JLabel("Login: "+user.email())); top.add(new JLabel("Grupo: "+user.group().displayName())); top.add(new JLabel("Nome: "+user.name()));
        top.add(Box.createVerticalStrut(10)); top.add(count); updateUser(user);
        JPanel path = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); path.add(new JLabel("Caminho da pasta:")); path.add(folder); path.add(browse);
        JPanel pass = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); pass.add(new JLabel("Frase secreta:")); pass.add(phrase); pass.add(list);
        top.add(Box.createVerticalStrut(12)); top.add(path); top.add(Box.createVerticalStrut(8)); top.add(pass);
        for (Component child : top.getComponents()) { if (child instanceof JComponent component) { component.setAlignmentX(Component.LEFT_ALIGNMENT); } }
        add(top, BorderLayout.NORTH);
        table = new JTable(model); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(1).setPreferredWidth(240); table.setRowHeight(24);
        var scroll = new JScrollPane(table); scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setPreferredSize(new Dimension(750, 220)); add(scroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(6, 10)); bottom.add(status, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.add(extract); buttons.add(back); bottom.add(buttons, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        table.getSelectionModel().addListSelectionListener(e -> extract.setEnabled(!busy && table.getSelectedRow() >= 0)); extract.setEnabled(false);
    }
    void updateUser(User user) { count.setText("Total de consultas do usuário: "+user.consultationCount()); }
    void showListing(VaultService.Listing listing) {
        clearListing(); entries = listing.files();
        for (var entry : entries) { model.addRow(new Object[]{entry.filename(), entry.owner(), entry.group().catalogLabel()}); }
        updateUser(listing.user()); status.setText(entries.size()+" arquivo(s) disponível(is). Selecione um arquivo para extrair.");
    }
    String selectedCode() { int row = table.getSelectedRow(); return row < 0 ? null : entries.get(table.convertRowIndexToModel(row)).code(); }
    void clearListing() { entries = List.of(); model.setRowCount(0); extract.setEnabled(false); }
    void clear() { phrase.setText(""); clearListing(); }
    void setBusy(boolean value) {
        busy = value; folder.setEnabled(!value); phrase.setEnabled(!value); browse.setEnabled(!value); list.setEnabled(!value); back.setEnabled(!value);
        table.setEnabled(!value); extract.setEnabled(!value && table.getSelectedRow() >= 0);
        status.setText(value ? "Processando…" : "Pronto.");
    }
}
