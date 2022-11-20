import java.util.ArrayList;


class ConcreteData implements Data{
    private final int id;
    private final int size;
    private final ArrayList<Integer> data;

    public ConcreteData(final int id, final int size, final ArrayList<Integer> data)
    {
        this.id = id;
        this.size = size;
        this.data = data;
    }

    @Override
    public int getDataId()
    {
        return id;
    }

    @Override
    public int getSize(){
        return size;
    }

  @Override
    public int getValue(int idx)
  {
        return data.get(idx);
  }

}
