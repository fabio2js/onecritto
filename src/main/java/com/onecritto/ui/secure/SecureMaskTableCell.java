package com.onecritto.ui.secure;

import com.onecritto.model.SecretEntry;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;

import java.util.function.Function;

public class SecureMaskTableCell extends TableCell<SecretEntry, String> {

    private final Label lbl = new Label();
    private final Function<SecretEntry, char[]> extractor;

    public SecureMaskTableCell(Function<SecretEntry, char[]> extractor) {

        this.extractor = extractor;

        lbl.setWrapText(false);
        lbl.setStyle("-fx-text-fill: #444;");
        lbl.setTextOverrun(OverrunStyle.CLIP);  // niente "..."
        lbl.setEllipsisString("");
        setGraphic(lbl);

        // disattiva il testo interno a TableCell
        setText(null);

    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || getTableRow() == null) {
            lbl.setText(null);
            return;
        }

        SecretEntry entry = getTableRow().getItem();
        if (entry == null) {
            lbl.setText(null);
            return;
        }

        // estrai username o notes usando la lambda passata
        char[] data = extractor.apply(entry);

        if (data == null || data.length == 0) {
            lbl.setText("");
            return;
        }

        // mask completa
        lbl.setText("•".repeat(data.length));
    }
}
