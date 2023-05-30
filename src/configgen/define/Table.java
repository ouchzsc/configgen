package configgen.define;

import configgen.Node;
import configgen.util.DomUtils;
import configgen.view.DefineView;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class Table extends Node {
    public enum EnumType {
        None,
        /**
         * 全枚举：生成时就是枚举，
         * 可以switch case，配合静态类型检查，可方便提示逻辑是否漏掉了一种情况的处理。
         * 同时多态类必须配置枚举，方便switch case
         */
        EnumFull,
        /**
         * 入口：生成时是通过类的static成员，来访问csv的某一行。
         * 这样代码避免了使用魔数。也不用运行时检查是否为null，因为启动时保证必不为null
         */
        Entry,
    }

    public Bean bean;

    public String[] primaryKey;
    public boolean isPrimaryKeySeq;

    public EnumType enumType;
    public String enumStr;
    /**
     * 0 是部分，1是额外分出一个文件。lua生成assets.lua时报错，因为lua单个文件不能多余65526个constant，所以这里分割一下
     */
    private int extraSplit = 0;
    public boolean isColumnMode;

    public final Map<String, UniqueKey> uniqueKeys = new LinkedHashMap<>();

    Table(Define parent, Element self) {
        super(parent, parent.wrapPkgName(self.getAttribute("name")));
        DomUtils.permitAttributes(self, "name", "own", "primaryKey", "isPrimaryKeySeq",
                "enum", "enumPart", "entry", "extraSplit", "isColumnMode");
        DomUtils.permitElements(self, "column", "foreignKey", "range", "uniqueKey");

        require(self.hasAttribute("name"), "table必须设置name");
        require(!name.isEmpty(), "name不能是空字符串");

        bean = new Bean(this, parent, self);

        if (self.hasAttribute("primaryKey")) {
            primaryKey = DomUtils.parseStringArray(self, "primaryKey");
        } else {
            primaryKey = new String[]{bean.columns.keySet().iterator().next()};
        }
        isPrimaryKeySeq = self.hasAttribute("isPrimaryKeySeq");


        if (self.hasAttribute("enum")) {
            enumType = EnumType.EnumFull;
            enumStr = self.getAttribute("enum");
            require(!enumStr.isEmpty(), "enum 不能为空");
        } else if (self.hasAttribute("entry") || self.hasAttribute("enumPart")) {
            enumType = EnumType.Entry;
            enumStr = self.hasAttribute("entry") ?
                    self.getAttribute("entry") : self.getAttribute("enumPart");
            require(!enumStr.isEmpty(), "entry 不能为空");
        } else {
            enumType = EnumType.None;
            enumStr = "";
        }

        if (self.hasAttribute("extraSplit")) {
            extraSplit = Integer.parseInt(self.getAttribute("extraSplit"));
        }

        isColumnMode = self.hasAttribute("isColumnMode");

        for (Element ele : DomUtils.elements(self, "uniqueKey")) {
            UniqueKey uk = new UniqueKey(this, ele);
            require(!Arrays.equals(uk.keys, primaryKey), "uniqueKey和primaryKey重复", uk);
            UniqueKey old = uniqueKeys.put(uk.toString(), uk);
            require(old == null, "uniqueKey重复", uk);
        }
    }

    public boolean isEnum() {
        return enumType != EnumType.None;
    }

    public boolean isEnumFull() {
        return enumType == EnumType.EnumFull;
    }

    public boolean isEnumPart() {
        return enumType == EnumType.Entry;
    }

    public boolean isEnumAsPrimaryKey() {
        return primaryKey.length == 1 && primaryKey[0].equals(enumStr);
    }

    public boolean isEnumHasOnlyPrimaryKeyAndEnumStr() {
        if (enumType != EnumType.None) {
            if (bean.columns.size() > 2 || isEnumAsPrimaryKey() && bean.columns.size() > 1) {
                return false;
            }
            return !hasAnyForeignKey();
        }
        return false;
    }

    private boolean hasAnyForeignKey() {
        return !bean.foreignKeys.isEmpty()
                || bean.columns.values().stream().anyMatch(column -> null != column.foreignKey);
    }


    public int getExtraSplit() {
        return extraSplit;
    }

    Table(Define parent, String name) { // 新csv，产生新table定义
        super(parent, name);
        bean = new Bean(this, parent, name);
        enumType = EnumType.None;
        enumStr = "";
        primaryKey = new String[0];
        isPrimaryKeySeq = false;
        extraSplit = 0;
    }


    public void verifyDefine(AllDefine fullDefine) {
        bean.verifyDefine(fullDefine);
    }

    //////////////////////////////// auto fix使用的接口

    public LinkedHashMap<String, Column> getColumnMapCopy() {
        return new LinkedHashMap<>(bean.columns);
    }

    public void clearColumns() {
        bean.columns.clear();
    }

    public boolean addColumn(Column column, String newColumnDesc) {
        boolean changed = false;
        if (!column.desc.equals(newColumnDesc)) {
            column.setDesc(newColumnDesc);
            changed = true;
        }
        bean.columns.put(column.name, column);
        return changed;
    }

    public Column addNewColumn(String colName, String colType, String colDesc) {
        Column c = new Column(bean, colName, colType, colDesc);
        bean.columns.put(colName, c);
        return c;
    }

    public void autoFixDefine(AllDefine defineToFix) {
        bean.autoFixDefine(defineToFix);
    }


    //////////////////////////////// extract

    private Table(DefineView _parent, Table original) {
        super(_parent, original.name);
        enumType = original.enumType;
        enumStr = original.enumStr;
        primaryKey = original.primaryKey;
        isPrimaryKeySeq = original.isPrimaryKeySeq;
        extraSplit = original.extraSplit;
        isColumnMode = original.isColumnMode;
    }

    Table extract(DefineView defineView) {
        Table part = new Table(defineView, this);
        part.bean = bean.extract(part, defineView);
        Objects.requireNonNull(part.bean);

        uniqueKeys.forEach((n, uk) -> {
            if (part.bean.columns.keySet().containsAll(Arrays.asList(uk.keys))) {
                part.uniqueKeys.put(n, new UniqueKey(part, uk));
            }
        });
        return part;
    }

    public void resolveExtract(DefineView defineView) {
        bean.resolveExtract(defineView);
        String original = enumStr;
        if (!bean.columns.containsKey(original)) {
            enumType = EnumType.None;
            enumStr = "";
        }
        require(bean.columns.keySet().containsAll(Arrays.asList(primaryKey)), "必须own主键=" + String.join(",", primaryKey));
    }

    //////////////////////////////// save

    void save(Element parent) {
        Element self = DomUtils.newChild(parent, "table");
        uniqueKeys.values().forEach(c -> c.save(self));
        bean.update(self);

        if (primaryKey.length > 0) {
            self.setAttribute("primaryKey", String.join(",", primaryKey));
        }
        if (isPrimaryKeySeq) {
            self.setAttribute("isPrimaryKeySeq", "true");
        }

        switch (enumType) {
            case None:
                break;
            case EnumFull:
                self.setAttribute("enum", enumStr);
                break;
            case Entry:
                self.setAttribute("entry", enumStr);
                break;
        }
        if (extraSplit > 0) {
            self.setAttribute("extraSplit", String.valueOf(extraSplit));
        }
        if (isColumnMode) {
            self.setAttribute("isColumnMode", "1");
        }
    }
}
