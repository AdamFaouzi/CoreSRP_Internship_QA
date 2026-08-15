package com.coresrp.qa.ui.tests.boundary;

import com.coresrp.qa.ui.base.BaseTest;
import com.coresrp.qa.ui.pages.DocumentsPage;
import com.microsoft.playwright.Download;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Follow-up to CsvFormulaInjectionTest: the CSV export was confirmed vulnerable to formula
 * injection. Does the SAME already-stored payload survive dangerously into the Excel (.xlsx) and
 * JSON exports too, or are those formats safe? And does the negative-total data-integrity issue
 * (finding: grand_total = -500 on a non-return invoice) propagate into every export format?
 *
 * This is a multi-step, cross-format integrity check reusing invoices ALREADY tainted in the
 * account during earlier testing (the formula-vendor invoice from CsvFormulaInjectionTest and the
 * -500 invoice from InvoiceAmountDateTest) — no new uploads, no quota spent. Parses the real
 * binary .xlsx with Apache POI to inspect actual cell types, not just raw text.
 *
 * Nuance being tested precisely: a .xlsx STRING cell whose text starts with "=" is displayed
 * literally by Excel (safe by format) — only a genuine FORMULA cell (an <f> element) is evaluated.
 * CSV is different: Excel parses CSV text and auto-detects "=" as a formula. So the correct,
 * credible finding is likely "CSV-specific, not all exports" — this test confirms or refutes that.
 */
public class ExportFormatIntegrityTest extends BaseTest {

    private static final String FORMULA_MARKER = "cmd|' /C calc'!A0"; // distinctive tail of the stored payload

    @Test
    void excelExport_formulaVendorIsNotStoredAsAnEvaluableFormulaCell() throws IOException {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("The stored formula-injection vendor name appears in the .xlsx export only as an " +
                "inert STRING cell, never as an evaluable FORMULA cell — an actual formula cell " +
                "would make the Excel export as dangerous as the CSV one (worse: it auto-evaluates on open)");

        Download download = page.waitForDownload(documents::exportExcel);
        boolean foundPayload = false;
        boolean asFormulaCell = false;
        boolean asStringStartingWithTrigger = false;
        String exactCellFormula = null;

        try (InputStream in = Files.newInputStream(download.path());
             Workbook wb = new XSSFWorkbook(in)) {
            for (Sheet sheet : wb) {
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String text = cellText(cell);
                        if (text.contains(FORMULA_MARKER)) {
                            foundPayload = true;
                            if (cell.getCellType() == CellType.FORMULA) {
                                asFormulaCell = true;
                                exactCellFormula = cell.getCellFormula();
                            }
                            String trimmed = text.stripLeading();
                            if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
                                asStringStartingWithTrigger = true;
                            }
                        }
                    }
                }
            }
        }

        actual("payload present in xlsx: " + foundPayload + " | stored as FORMULA cell (dangerous): "
                + asFormulaCell + " | exact cell formula: " + exactCellFormula
                + " | stored as STRING starting with a trigger char: " + asStringStartingWithTrigger);

        if (asFormulaCell) {
            recordFinding("HIGH",
                    "Excel (.xlsx) export writes an injected vendor name as an evaluable FORMULA cell",
                    "A vendor name beginning with '=' is written into the .xlsx export as a genuine formula cell " +
                            "(POI reports CellType.FORMULA, exact formula: " + exactCellFormula + "). Excel auto-" +
                            "evaluates formula cells on open with no user action — strictly worse than the CSV " +
                            "injection, which at least depends on Excel's CSV text auto-detection. Same root cause " +
                            "(no formula-trigger-char neutralization on export) but a more direct execution path.");
        }
        assertFalse(asFormulaCell, "injected vendor name must not become an evaluable Excel formula cell");
    }

    @Test
    void jsonExport_carriesNegativeTotalAndTreatsFormulaVendorAsInertData() throws IOException {
        DocumentsPage documents = loginAndOpenDocuments();

        expect("JSON export is inert data (no formula-execution risk by format), but should still " +
                "faithfully reflect stored values — confirming the negative -500 total propagates " +
                "into JSON too (data-integrity reach of that finding) and the formula vendor is just a string");

        Download download = page.waitForDownload(documents::exportJson);
        String json = Files.readString(download.path());
        actual("json export length: " + json.length() + " | contains -500: " + json.contains("-500")
                + " | contains formula-vendor marker: " + json.contains(FORMULA_MARKER));

        // Not a vulnerability assertion — documents that the negative-total finding's blast radius
        // includes the JSON export path (the format the app says feeds ERP systems).
        assertTrue(json.contains("-500"),
                "the negative total from the earlier finding should be visible in the JSON export, confirming its reach");
    }

    private static String cellText(Cell cell) {
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue();
                case FORMULA -> cell.getCellFormula();
                case NUMERIC -> String.valueOf(cell.getNumericCellValue());
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }
}
