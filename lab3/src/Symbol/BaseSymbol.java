package Symbol;

import Type.Type;
import org.antlr.v4.runtime.misc.Pair;

import java.util.ArrayList;

public class BaseSymbol implements Symbol{
    private String name;
    private Type type;

    private ArrayList<Pair<Integer, Integer>> locations = new ArrayList<>();

    public BaseSymbol(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type getType() {
        return type;
    }

    public ArrayList<Pair<Integer, Integer>> getLocations() {
        return locations;
    }

    public void addLocation(int line, int column) {
        Pair<Integer, Integer> location = new Pair<>(line, column);
        locations.add(location);
    }


    public boolean isTheSymbol(Pair<Integer, Integer> location) {
        return locations.contains(location);
    }
}
