package pt.tecnico.ulisboa.cnv.model;

public class DbEntry {
    String identifier;
    int height;
    int width;
    int x0;
    int x1;
    int y0;
    int y1;
    int xS;
    int yS;
    String input;
    String strategy;
    long cost;

    boolean sameViewport = false;


    public DbEntry() {}

    /**
     *  Copy constructor to avoid the change of the
     *  "isSameViewPort" on the wrong object...
     */
    public DbEntry(DbEntry entry) {
        this.identifier = entry.identifier;
        this.height = entry.height;
        this.width = entry.width;
        this.x0 = entry.x0;
        this.x1 = entry.x1;
        this.y0 = entry.y0;
        this.y1 = entry.y1;
        this.xS = entry.xS;
        this.yS = entry.yS;
        this.input = entry.input;
        this.strategy = entry.strategy;
        this.cost = entry.cost;
    }

    public String getStrategy() {
        return strategy;
    }

    public int getyS() {
        return yS;
    }

    public int getY1() {
        return y1;
    }

    public int getY0() {
        return y0;
    }

    public int getxS() {
        return xS;
    }

    public int getX1() {
        return x1;
    }

    public int getX0() {
        return x0;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getCost() {
        return cost;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getInput() {
        return input;
    }

    public void setyS(int yS) {
        this.yS = yS;
    }

    public void setY1(int y1) {
        this.y1 = y1;
    }

    public void setY0(int y0) {
        this.y0 = y0;
    }

    public void setxS(int xS) {
        this.xS = xS;
    }

    public void setX1(int x1) {
        this.x1 = x1;
    }

    public void setX0(int x0) {
        this.x0 = x0;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public void setSameViewport(boolean sameViewport) {
        this.sameViewport = sameViewport;
    }

    public boolean isSameViewport() {
        return sameViewport;
    }

}
