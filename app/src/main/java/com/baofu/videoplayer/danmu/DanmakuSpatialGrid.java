package com.baofu.videoplayer.danmu;

import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
// DanmakuSpatialGrid.java
public class DanmakuSpatialGrid {
    private int cellSize = 100;
    private int cols, rows;
    private List<List<List<DanmakuItem>>> grid;
    private int viewWidth, viewHeight;

    public DanmakuSpatialGrid(int viewWidth, int viewHeight) {
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        initGrid();
    }

    private void initGrid() {
        cols = (int) Math.ceil(viewWidth / (float) cellSize);
        rows = (int) Math.ceil(viewHeight / (float) cellSize);

        grid = new ArrayList<>(cols);
        for (int i = 0; i < cols; i++) {
            List<List<DanmakuItem>> column = new ArrayList<>(rows);
            for (int j = 0; j < rows; j++) {
                column.add(new ArrayList<>());
            }
            grid.add(column);
        }
    }

    public void clear() {
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                grid.get(i).get(j).clear();
            }
        }
    }

    public void insert(DanmakuItem item) {
        RectF bounds = item.getClickArea();
        if (bounds == null) return;

        int startCol = Math.max(0, (int) (bounds.left / cellSize));
        int endCol = Math.min(cols - 1, (int) (bounds.right / cellSize));
        int startRow = Math.max(0, (int) (bounds.top / cellSize));
        int endRow = Math.min(rows - 1, (int) (bounds.bottom / cellSize));

        for (int i = startCol; i <= endCol; i++) {
            for (int j = startRow; j <= endRow; j++) {
                grid.get(i).get(j).add(item);
            }
        }
    }

    public void remove(DanmakuItem item) {
        RectF bounds = item.getClickArea();
        if (bounds == null) return;

        int startCol = Math.max(0, (int) (bounds.left / cellSize));
        int endCol = Math.min(cols - 1, (int) (bounds.right / cellSize));
        int startRow = Math.max(0, (int) (bounds.top / cellSize));
        int endRow = Math.min(rows - 1, (int) (bounds.bottom / cellSize));

        for (int i = startCol; i <= endCol; i++) {
            for (int j = startRow; j <= endRow; j++) {
                grid.get(i).get(j).remove(item);
            }
        }
    }

    public List<DanmakuItem> query(float x, float y) {
        int col = (int) (x / cellSize);
        int row = (int) (y / cellSize);

        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return Collections.emptyList();
        }

        return grid.get(col).get(row);
    }

    public void updateSize(int width, int height) {
        if (width != viewWidth || height != viewHeight) {
            this.viewWidth = width;
            this.viewHeight = height;
            initGrid();
        }
    }

    public int getCellCount() {
        return cols * rows;
    }

    public int getItemCount() {
        int count = 0;
        Set<DanmakuItem> items = new HashSet<>();

        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                items.addAll(grid.get(i).get(j));
            }
        }

        return items.size();
    }
}