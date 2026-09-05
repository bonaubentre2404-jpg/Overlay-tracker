package com.survival.overlay;

import android.graphics.Bitmap;
import android.graphics.Color;

public class MarkerDetector {
    private static final int GRID_SIZE = 18;
    private static final int BLUE_THRESHOLD = 80;
    private static final float COMPACTNESS_MIN = 0.25f;
    private static final float COMPACTNESS_MAX = 0.75f;
    private static final float ASPECT_RATIO_MIN = 0.7f;
    private static final float ASPECT_RATIO_MAX = 1.4f;

    public float[] detect(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int cols = w / GRID_SIZE;
        int rows = h / GRID_SIZE;
        if (cols < 3 || rows < 3) return null;

        boolean[][] blueMask = new boolean[rows][cols];
        int totalBluePixels = 0;
        int sumX = 0, sumY = 0;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int startX = c * GRID_SIZE;
                int startY = r * GRID_SIZE;
                int rSum = 0, gSum = 0, bSum = 0;
                int count = 0;
                for (int dy = 0; dy < GRID_SIZE; dy++) {
                    for (int dx = 0; dx < GRID_SIZE; dx++) {
                        int x = startX + dx;
                        int y = startY + dy;
                        if (x < w && y < h) {
                            int pixel = bitmap.getPixel(x, y);
                            rSum += Color.red(pixel);
                            gSum += Color.green(pixel);
                            bSum += Color.blue(pixel);
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    int avgR = rSum / count;
                    int avgG = gSum / count;
                    int avgB = bSum / count;
                    if (avgB - Math.max(avgR, avgG) > BLUE_THRESHOLD) {
                        blueMask[r][c] = true;
                        totalBluePixels++;
                        sumX += c;
                        sumY += r;
                    }
                }
            }
        }

        if (totalBluePixels < 10) return null;

        float cx = (float) sumX / totalBluePixels;
        float cy = (float) sumY / totalBluePixels;
        cx = cx * GRID_SIZE + GRID_SIZE/2f;
        cy = cy * GRID_SIZE + GRID_SIZE/2f;

        int minC = cols, maxC = -1, minR = rows, maxR = -1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (blueMask[r][c]) {
                    if (c < minC) minC = c;
                    if (c > maxC) maxC = c;
                    if (r < minR) minR = r;
                    if (r > maxR) maxR = r;
                }
            }
        }
        if (minC > maxC || minR > maxR) return null;

        int boxW = (maxC - minC + 1) * GRID_SIZE;
        int boxH = (maxR - minR + 1) * GRID_SIZE;
        float aspect = (float) boxW / boxH;
        if (aspect < ASPECT_RATIO_MIN || aspect > ASPECT_RATIO_MAX) return null;

        int area = totalBluePixels * GRID_SIZE * GRID_SIZE;
        int bboxArea = boxW * boxH;
        float compactness = (bboxArea > 0) ? (float) area / bboxArea : 0;
        if (compactness < COMPACTNESS_MIN || compactness > COMPACTNESS_MAX) return null;

        return new float[]{cx, cy};
    }
}
