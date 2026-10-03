package com.ninsky.cronos.domain.model.imports;

/**
 * What a bulk import loads. {@code sheetName} is the worksheet read from the workbook (other sheets,
 * e.g. instructions, are ignored); {@code templateFile} is the downloadable template on the classpath.
 */
public enum ImportResource {
    UNIT_TYPE("UnitTypes", "imports/templates/unit-types-template.xlsx"),
    MEASUREMENT_UNIT("MeasurementUnits", "imports/templates/measurement-units-template.xlsx");

    private final String sheetName;
    private final String templateFile;

    ImportResource(String sheetName, String templateFile) {
        this.sheetName = sheetName;
        this.templateFile = templateFile;
    }

    public String sheetName() {
        return sheetName;
    }

    public String templateFile() {
        return templateFile;
    }
}
