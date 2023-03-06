package Type;

import java.util.ArrayList;

public class FunctionType implements Type{
    private Type returnType;
    private final ArrayList<Type> paramsType = new ArrayList<>();

    public void setReturnType(Type returnType) {
        this.returnType = returnType;
    }

    public Type getReturnType() {
        return returnType;
    }

    public ArrayList<Type> getParamsType() {
        return paramsType;
    }

    public void addParam(Type type) {
        paramsType.add(type);
    }
}
