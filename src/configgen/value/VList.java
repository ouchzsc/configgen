package configgen.value;

import configgen.CSV;
import configgen.Node;
import configgen.type.TList;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class VList extends Value {
    public final List<Value> list = new ArrayList<>();

    public VList(Node parent, String link, List<Value> vs) { // for keys and keysRef
        super(parent, link, null, toRaw(vs));
        list.addAll(vs);
    }

    private static List<Cell> toRaw(List<Value> vs) {
        List<Cell> res = new ArrayList<>();
        for (Value v : vs) {
            res.addAll(v.cells);
        }
        return res;
    }

    public VList(Node parent, String link, TList type, List<Cell> data) {
        super(parent, link, type, data);

        List<Cell> parsed;
        if (type.count == 0) { //compress
            Assert(data.size() == 1);
            Cell dat = data.get(0);
            parsed = CSV.parseList(dat.data).stream().map(s -> new Cell(dat.row, dat.col, s)).collect(Collectors.toList());
        } else {
            Assert(data.size() == type.columnSpan());
            parsed = data;
        }

        int vc = type.value.columnSpan();
        int idx = 0;
        for (int i = 0; i < type.count; i++) {
            int s = i * vc;
            if (!parsed.get(s).data.isEmpty()) { //first as a null clue, see code generator
                list.add(Value.create(this, String.valueOf(idx), type.value, parsed.subList(s, s + vc)));
                idx++;
            } else {
                for (Cell dc : parsed.subList(s, s + vc)) {
                    Assert(dc.data.isEmpty(), "list value ignored by first cell empty, but part filled, " + dc);
                }
            }
        }
    }

    @Override
    public void accept(ValueVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void verifyConstraint() {
        list.forEach(Value::verifyConstraint);
    }

    @Override
    public boolean equals(Object o) {
        return o != null && o instanceof VList && list.equals(((VList) o).list);
    }

    @Override
    public int hashCode() {
        return list.hashCode();
    }

}
