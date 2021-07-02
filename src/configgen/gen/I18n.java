package configgen.gen;

import configgen.util.EFileFormat;
import configgen.util.SheetData;
import configgen.util.SheetHandler;
import configgen.util.SheetUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class I18n {

    public interface Collector {
        void enterTable(String table);

        void enterText(String original, String text);
    }

    private Map<String, Map<String, String>> map = null;
    private Map<String, String> curTable = null;
    private boolean isCRLFAsLF;


    private Collector collector;

    I18n() {
    }

    I18n(String file, String encoding, boolean crlfaslf) {
        this(Paths.get(file), encoding, crlfaslf);
    }

    I18n(Path path, String encoding, boolean crlfaslf) {
        map = new HashMap<>();
        List<SheetData> sheetDataList = SheetUtils.readFromFile(path.toFile(), new ReadOption(encoding));
        if (sheetDataList.size() == 0) {
            throw new IllegalArgumentException("国际化i18n文件为空");
        }
        if (sheetDataList.size() > 1) {
            throw new IllegalArgumentException("国际化i18n文件有多个");
        }
        List<List<String>> rows = sheetDataList.get(0).rows;

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("国际化i18n文件为空");
        }
        List<String> row0 = rows.get(0);
        if (row0.size() != 3) {
            throw new IllegalArgumentException("国际化i18n文件列数不为3");
        }

        isCRLFAsLF = crlfaslf;
        for (List<String> row : rows) {
            if (row.isEmpty()) {
                continue;
            }
            if (row.size() != 3) {
                System.out.println(row + " 不是3列，被忽略");
            } else {
                String table = row.get(0);
                String raw = row.get(1);
                String i18 = row.get(2);
                raw = normalizeRaw(raw);

                Map<String, String> m = map.computeIfAbsent(table, k -> new HashMap<>());
                m.put(raw, i18);
            }
        }
    }


    private String normalizeRaw(String raw) {
        if (isCRLFAsLF) {
            return raw.replaceAll("\r\n", "\n");
        } else {
            return raw;
        }
    }

    public void setCollector(Collector collector) {
        this.collector = collector;
    }

    public void enterTable(String table) {
        if (collector != null) {
            collector.enterTable(table);
        }

        if (map == null) {
            return;
        }
        curTable = map.get(table);
    }

    // 没找到或是原字符串是空字符串，则返回null
    public String enterText(String raw) {
        if (collector == null && curTable == null) {
            return null;
        }

        raw = normalizeRaw(raw);
        String text;
        String res;
        if (curTable != null) {
            text = curTable.get(raw);
            if (text != null && !text.isEmpty()) {
                res = text;
            } else {
                res = null;
                text = "";
            }
        } else {
            text = "";
            res = null;
        }

        if (collector != null) {
            collector.enterText(raw, text);
        }
        return res;
    }


    static class ReadOption extends SheetHandler.DefaultReadOption {

        public ReadOption(String dataEncoding) {
            super(dataEncoding);
        }

        @Override
        public boolean acceptSheet(EFileFormat format, File file, String sheetName) {
            // Excel只支持sheet名称和文件名称一样
            if (format == EFileFormat.EXCEL) {
                String fileName = file.getName();
                int i = fileName.lastIndexOf(".");
                if (i < 0) {
                    return fileName.equals(sheetName);
                } else {
                    return fileName.substring(0, i).equals(sheetName);
                }
            }
            return true;
        }
    }
}

