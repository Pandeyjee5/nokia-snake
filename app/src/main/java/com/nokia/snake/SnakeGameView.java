package com.nokia.snake;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.content.SharedPreferences;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

public class SnakeGameView extends View {
    // A deliberately simple, old-phone-style game: monochrome LCD, chunky pixels, keypad controls.
    private static final int COLS = 18;
    private static final int ROWS = 24;
    private static final int MAX_LEN = COLS * ROWS;
    private static final int BG = Color.rgb(185, 195, 154);
    private static final int DARK = Color.rgb(49, 78, 43);
    private static final int LIGHT = Color.rgb(202, 211, 170);
    private static final int BORDER = Color.rgb(78, 101, 63);

    private enum Dir { UP, DOWN, LEFT, RIGHT }
    private enum State { TITLE, PLAYING, PAUSED, GAME_OVER }

    private static class Cell {
        int x, y;
        Cell(int x, int y) { this.x = x; this.y = y; }
        Cell copy() { return new Cell(x, y); }
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pixel = new Paint();
    private final Random random = new Random();
    private final Handler handler = new Handler();
    private final Deque<Cell> snake = new ArrayDeque<>();
    private final SharedPreferences prefs;
    private final RectF screen = new RectF();
    private final Runnable tick = this::step;

    private State state = State.TITLE;
    private Dir dir = Dir.RIGHT;
    private Dir nextDir = Dir.RIGHT;
    private Cell food;
    private int score = 0;
    private int highScore = 0;
    private int level = 1;
    private long speed = 180;
    private boolean sound = true;
    private boolean keyTouchActive = false;
    private float downX, downY;

    public SnakeGameView(Context context) {
        super(context);
        setFocusable(true);
        prefs = context.getSharedPreferences("snake", Context.MODE_PRIVATE);
        highScore = prefs.getInt("highScore", 0);
        p.setTypeface(Typeface.MONOSPACE);
        pixel.setAntiAlias(false);
        setBackgroundColor(BG);
    }

    private void startGame() {
        handler.removeCallbacks(tick);
        snake.clear();
        int sx = COLS / 2;
        int sy = ROWS / 2;
        snake.addFirst(new Cell(sx, sy));
        snake.addLast(new Cell(sx - 1, sy));
        snake.addLast(new Cell(sx - 2, sy));
        dir = Dir.RIGHT;
        nextDir = Dir.RIGHT;
        score = 0;
        level = 1;
        speed = 180;
        placeFood();
        state = State.PLAYING;
        invalidate();
        handler.postDelayed(tick, speed);
    }

    private void placeFood() {
        for (int tries = 0; tries < 500; tries++) {
            Cell c = new Cell(random.nextInt(COLS), random.nextInt(ROWS));
            boolean hit = false;
            for (Cell s : snake) {
                if (s.x == c.x && s.y == c.y) { hit = true; break; }
            }
            if (!hit) { food = c; return; }
        }
        food = new Cell(1, 1);
    }

    private void step() {
        if (state != State.PLAYING) return;
        dir = nextDir;
        Cell head = snake.peekFirst().copy();
        switch (dir) {
            case UP: head.y--; break;
            case DOWN: head.y++; break;
            case LEFT: head.x--; break;
            case RIGHT: head.x++; break;
        }

        // Classic Nokia-style hard walls.
        if (head.x < 0 || head.x >= COLS || head.y < 0 || head.y >= ROWS || hitsBody(head)) {
            state = State.GAME_OVER;
            if (score > highScore) {
                highScore = score;
                prefs.edit().putInt("highScore", highScore).apply();
            }
            ((SnakeActivity)getContext()).buzz(120);
            invalidate();
            return;
        }

        snake.addFirst(head);
        if (food != null && head.x == food.x && head.y == food.y) {
            score += 10;
            level = Math.min(9, 1 + score / 50);
            speed = Math.max(65, 180 - (level - 1) * 14L);
            if (sound) ((SnakeActivity)getContext()).buzz(25);
            placeFood();
        } else {
            snake.removeLast();
        }
        invalidate();
        handler.postDelayed(tick, speed);
    }

    private boolean hitsBody(Cell head) {
        int i = 0;
        for (Cell s : snake) {
            // Allow moving into the old tail position because it is removed this tick.
            if (i == snake.size() - 1) break;
            if (s.x == head.x && s.y == head.y) return true;
            i++;
        }
        return false;
    }

    private void changeDirection(Dir d) {
        if (state == State.TITLE || state == State.GAME_OVER) {
            startGame();
            if (state != State.PLAYING) return;
        }
        if (state != State.PLAYING) return;
        if ((dir == Dir.UP && d == Dir.DOWN) || (dir == Dir.DOWN && d == Dir.UP)
                || (dir == Dir.LEFT && d == Dir.RIGHT) || (dir == Dir.RIGHT && d == Dir.LEFT)) return;
        nextDir = d;
    }

    private void togglePause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            handler.removeCallbacks(tick);
        } else if (state == State.PAUSED) {
            state = State.PLAYING;
            handler.postDelayed(tick, speed);
        }
        invalidate();
    }

    public void pauseForLifecycle() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            handler.removeCallbacks(tick);
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();
        screen.set(0, 0, w, h);
        c.drawColor(BG);

        // Old LCD top label and speaker-like separator.
        drawHeader(c, w);
        drawGameScreen(c, w);
        drawKeypad(c, w, h);
    }

    private void drawHeader(Canvas c, float w) {
        p.setColor(DARK);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(18);
        c.drawText("SNAKE", 18, 28, p);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(14);
        c.drawText("HI " + String.format("%04d", highScore), w - 18, 28, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        c.drawLine(14, 39, w - 14, 39, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawGameScreen(Canvas c, float w) {
        float top = 50;
        float side = 16;
        float maxW = w - side * 2;
        float cell = maxW / COLS;
        float boardW = cell * COLS;
        float boardH = cell * ROWS;
        float left = (w - boardW) / 2f;
        float bottom = top + boardH;

        p.setColor(BORDER);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        c.drawRect(left - 3, top - 3, left + boardW + 3, bottom + 3, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(LIGHT);
        c.drawRect(left, top, left + boardW, bottom, p);

        // Very subtle LCD grid.
        pixel.setColor(Color.rgb(173, 183, 143));
        pixel.setStrokeWidth(1);
        for (int x = 0; x <= COLS; x++) c.drawLine(left + x * cell, top, left + x * cell, bottom, pixel);
        for (int y = 0; y <= ROWS; y++) c.drawLine(left, top + y * cell, left + boardW, top + y * cell, pixel);

        if (state == State.TITLE) {
            drawCenterMessage(c, left, top, boardW, boardH, "SNAKE", "PRESS ANY KEY");
            return;
        }

        if (food != null) {
            drawPixelFood(c, left + food.x * cell, top + food.y * cell, cell);
        }
        int i = 0;
        for (Cell s : snake) {
            float x = left + s.x * cell;
            float y = top + s.y * cell;
            p.setColor(DARK);
            float pad = Math.max(1, cell * 0.12f);
            c.drawRect(x + pad, y + pad, x + cell - pad, y + cell - pad, p);
            if (i == 0) {
                p.setColor(LIGHT);
                float eye = Math.max(1.5f, cell * 0.12f);
                c.drawRect(x + cell * 0.28f, y + cell * 0.27f, x + cell * 0.28f + eye, y + cell * 0.27f + eye, p);
                c.drawRect(x + cell * 0.62f, y + cell * 0.27f, x + cell * 0.62f + eye, y + cell * 0.27f + eye, p);
            }
            i++;
        }

        p.setColor(DARK);
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(13);
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText("SCORE " + String.format("%04d", score), left + 2, bottom + 22, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("LEVEL " + level, left + boardW - 2, bottom + 22, p);

        if (state == State.PAUSED) drawOverlay(c, left, top, boardW, boardH, "PAUSED", "PRESS P");
        if (state == State.GAME_OVER) drawOverlay(c, left, top, boardW, boardH, "GAME OVER", "PRESS ANY KEY");
    }

    private void drawPixelFood(Canvas c, float x, float y, float cell) {
        p.setColor(DARK);
        float a = cell * 0.22f;
        c.drawRect(x + a, y + a, x + cell - a, y + cell - a, p);
        p.setColor(LIGHT);
        c.drawRect(x + cell * .44f, y + cell * .28f, x + cell * .56f, y + cell * .72f, p);
    }

    private void drawCenterMessage(Canvas c, float left, float top, float w, float h, String big, String small) {
        p.setColor(DARK);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(Math.min(34, w / 7));
        c.drawText(big, left + w / 2, top + h / 2 - 8, p);
        p.setTextSize(12);
        c.drawText(small, left + w / 2, top + h / 2 + 22, p);
    }

    private void drawOverlay(Canvas c, float left, float top, float w, float h, String big, String small) {
        p.setColor(LIGHT);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(left + 18, top + h/2 - 42, left + w - 18, top + h/2 + 42, p);
        p.setColor(DARK);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        c.drawRect(left + 18, top + h/2 - 42, left + w - 18, top + h/2 + 42, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(21);
        c.drawText(big, left + w/2, top + h/2 - 7, p);
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(10);
        c.drawText(small, left + w/2, top + h/2 + 18, p);
    }

    private void drawKeypad(Canvas c, float w, float h) {
        float base = Math.max(0, h - 156);
        float bw = Math.min(74, w * .19f);
        float bh = 42;
        float gap = 8;
        float cx = w / 2;
        float cy = base + 28;
        drawButton(c, cx - bw/2, cy, bw, bh, "▲");
        drawButton(c, cx - bw/2 - bw - gap, cy, bw, bh, "◀");
        drawButton(c, cx + bw/2 + gap, cy, bw, bh, "▶");
        drawButton(c, cx - bw/2, cy + bh + gap, bw, bh, "▼");

        p.setColor(DARK);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(10);
        c.drawText("MENU", 30, h - 18, p);
        c.drawText("P = PAUSE", w/2, h - 18, p);
        c.drawText(sound ? "SOUND ON" : "SOUND OFF", w - 42, h - 18, p);
    }

    private void drawButton(Canvas c, float x, float y, float w, float h, String label) {
        p.setColor(BG);
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(new RectF(x, y, x+w, y+h), 8, 8, p);
        p.setColor(BORDER);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        c.drawRoundRect(new RectF(x, y, x+w, y+h), 8, 8, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(DARK);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        p.setTextSize(20);
        c.drawText(label, x+w/2, y+h/2+7, p);
    }

    private boolean inButton(float x, float y, float bx, float by, float bw, float bh) {
        return x >= bx && x <= bx+bw && y >= by && y <= by+bh;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float w = getWidth();
        float h = getHeight();
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            downX = e.getX(); downY = e.getY(); keyTouchActive = true;
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float x = e.getX(), y = e.getY();
            float dx = x - downX, dy = y - downY;
            if (Math.abs(dx) > 35 || Math.abs(dy) > 35) {
                if (Math.abs(dx) > Math.abs(dy)) changeDirection(dx > 0 ? Dir.RIGHT : Dir.LEFT);
                else changeDirection(dy > 0 ? Dir.DOWN : Dir.UP);
                return true;
            }
            float base = Math.max(0, h - 156);
            float bw = Math.min(74, w * .19f), bh = 42, gap = 8, cx = w/2, cy = base + 28;
            if (inButton(x,y,cx-bw/2,cy,bw,bh)) changeDirection(Dir.UP);
            else if (inButton(x,y,cx-bw/2-bw-gap,cy,bw,bh)) changeDirection(Dir.LEFT);
            else if (inButton(x,y,cx+bw/2+gap,cy,bw,bh)) changeDirection(Dir.RIGHT);
            else if (inButton(x,y,cx-bw/2,cy+bh+gap,bw,bh)) changeDirection(Dir.DOWN);
            else if (state == State.PLAYING && y < base) togglePause();
            else if (state == State.PAUSED) togglePause();
            else if (state == State.TITLE || state == State.GAME_OVER) startGame();
            invalidate();
            return true;
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_DPAD_UP: changeDirection(Dir.UP); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN: changeDirection(Dir.DOWN); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT: changeDirection(Dir.LEFT); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT: changeDirection(Dir.RIGHT); return true;
            case android.view.KeyEvent.KEYCODE_P: togglePause(); return true;
            case android.view.KeyEvent.KEYCODE_ENTER: if (state == State.TITLE || state == State.GAME_OVER) startGame(); return true;
            default: return super.onKeyDown(keyCode, event);
        }
    }
}
