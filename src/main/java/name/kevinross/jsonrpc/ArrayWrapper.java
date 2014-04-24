package name.kevinross.jsonrpc;

import java.util.Arrays;

public final class ArrayWrapper 
{
    private final Object[] array;

    public ArrayWrapper(final Object... array)
    {
        this.array = array;
    }

    public Object[] getArray()
    {
        return this.array;
    }

    public boolean equals(Object o)
    {
        if (o == null) return false;
        if (o == this) return true;
        if (o instanceof ArrayWrapper)
        {
            return Arrays.equals(this.array, ((ArrayWrapper)o).array);
        }
        return false;
    }

    private int hashCode(Object o)
    {
        if (o == null) return 0;
        return o.hashCode();
    }

    public int hashCode()
    {
        int sum = 17;
        if (this.array != null) for(int i = 0;i<this.array.length;i++)
        {
            sum = 37 * sum + this.hashCode(this.array[i]);
        }
        return sum;
    }

    public String toString()
    {
        if (this.array != null)
        {
            return "Wrapper " + Arrays.toString(array);
        }
        else return "Wrapper []";
    }
}