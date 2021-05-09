package pt.tecnico.ulisboa.cnv.model;

import java.util.ArrayList;

public class RequestArguments {
    // [-w, 512, -h, 512, -x0, 0, -x1, 64, -y0, 0, -y1, 64, -xS, 1, -yS, 2, -s, GRID_SCAN, -i, datasets/SIMPLE_VORONOI_512x512_1.png]
    // scan?w=2048&h=2048&x0=512&x1=1536&y0=512&y1=1536&xS=1024&yS=1024&s=GREEDY_RANGE_SCAN&i=SIMPLE_VORONOI_2048x2048_8.png

    private int width;
    private int height;
    private int x0;
    private int x1;
    private int y0;
    private int y1;
    private int xS;
    private int yS;
    private String strategy;
    private String input;

    public RequestArguments() { }

    public RequestArguments(String query) {
        String[] params = query.split("&");

        ArrayList<String> newArgs = new ArrayList<>();
        for (final String p : params) {
            String[] splitParam = p.split("=");
            newArgs.add(splitParam[1]);
        }

        width = Integer.parseInt(newArgs.get(0));
        height = Integer.parseInt(newArgs.get(1));
        x0 = Integer.parseInt(newArgs.get(2));
        x1 = Integer.parseInt(newArgs.get(3));
        y0 = Integer.parseInt(newArgs.get(4));
        y1 = Integer.parseInt(newArgs.get(5));
        xS = Integer.parseInt(newArgs.get(6));
        yS = Integer.parseInt(newArgs.get(7));
        strategy = newArgs.get(8);
    }

    public int calculateViewPort()
    {
        return (x1 - x0) * (y1 - y0);
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public int getX0() {
        return x0;
    }

    public int getX1() {
        return x1;
    }

    public int getxS() {
        return xS;
    }

    public int getY0() {
        return y0;
    }

    public int getY1() {
        return y1;
    }

    public int getyS() {
        return yS;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy.toLowerCase();
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setX0(int x0) {
        this.x0 = x0;
    }

    public void setX1(int x1) {
        this.x1 = x1;
    }

    public void setxS(int xS) {
        this.xS = xS;
    }

    public void setY0(int y0) {
        this.y0 = y0;
    }

    public void setY1(int y1) {
        this.y1 = y1;
    }

    public void setyS(int yS) {
        this.yS = yS;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }
}
