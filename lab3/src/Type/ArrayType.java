package Type;

public class ArrayType implements Type{
    private Type baseType;
    private int dimension;

    public ArrayType(Type baseType, int dimension) {
        this.baseType = baseType;
        this.dimension = dimension;
    }

    public Type getBaseType() {
        return baseType;
    }

    public int getDimension() {
        return dimension;
    }

}
