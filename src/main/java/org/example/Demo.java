package org.example;

import com.sun.jna.ptr.IntByReference;
import io.github.libsdl4j.api.event.SDL_Event;
import io.github.libsdl4j.api.rect.SDL_Rect;
import io.github.libsdl4j.api.render.SDL_Renderer;
import io.github.libsdl4j.api.video.SDL_Window;

import java.time.LocalTime;

import static io.github.libsdl4j.api.Sdl.SDL_Init;
import static io.github.libsdl4j.api.Sdl.SDL_Quit;
import static io.github.libsdl4j.api.SdlSubSystemConst.SDL_INIT_EVERYTHING;
import static io.github.libsdl4j.api.error.SdlError.SDL_GetError;
import static io.github.libsdl4j.api.event.SDL_EventType.*;
import static io.github.libsdl4j.api.event.SdlEvents.SDL_PollEvent;
import static io.github.libsdl4j.api.keycode.SDL_Keycode.*;
import static io.github.libsdl4j.api.render.SDL_RendererFlags.SDL_RENDERER_ACCELERATED;
import static io.github.libsdl4j.api.render.SdlRender.*;
import static io.github.libsdl4j.api.video.SDL_WindowFlags.SDL_WINDOW_RESIZABLE;
import static io.github.libsdl4j.api.video.SDL_WindowFlags.SDL_WINDOW_SHOWN;
import static io.github.libsdl4j.api.video.SdlVideo.SDL_CreateWindow;
import static io.github.libsdl4j.api.video.SdlVideo.SDL_GetWindowSize;
import static io.github.libsdl4j.api.video.SdlVideoConst.SDL_WINDOWPOS_CENTERED;

public class Demo {

    public static void main(String[] args) {
        // Initialize SDL
        int result = SDL_Init(SDL_INIT_EVERYTHING);
        if (result != 0) {
            throw new IllegalStateException("Unable to initialize SDL library (Error code " + result + "): " + SDL_GetError());
        }

        // Create and init the window
        SDL_Window window = SDL_CreateWindow("Demo SDL2", SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED, 1024, 768, SDL_WINDOW_SHOWN | SDL_WINDOW_RESIZABLE);
        if (window == null) {
            throw new IllegalStateException("Unable to create SDL window: " + SDL_GetError());
        }

        // Create and init the renderer
        SDL_Renderer renderer = SDL_CreateRenderer(window, -1, SDL_RENDERER_ACCELERATED);
        if (renderer == null) {
            throw new IllegalStateException("Unable to create SDL renderer: " + SDL_GetError());
        }

        int[][] board = new int[10][24];
        int cur_type = L;
        int cur_rot = 0;
        int cur_x = 5;
        int cur_y = 22;

        java.time.LocalTime next_render_time = java.time.LocalTime.now().minusSeconds(1);
        java.time.LocalTime next_down_time = java.time.LocalTime.now().plusSeconds(1);
        int lines_cleared = 0;
        int score = 0;
        boolean force_repaint = true;

        SDL_Event evt = new SDL_Event();
        boolean shouldRun = true;
        while (shouldRun) {
            boolean left = false;
            boolean right = false;
            boolean rotate = false;
            boolean down = false;
            boolean drop = false;
            while (SDL_PollEvent(evt) != 0) {
                switch (evt.type) {
                    case SDL_QUIT:
                        shouldRun = false;
                        break;
                    case SDL_KEYDOWN:
                        if (evt.key.keysym.sym ==  SDLK_UP)     rotate = true;
                        if (evt.key.keysym.sym ==  SDLK_DOWN)   down = true;
                        if (evt.key.keysym.sym ==  SDLK_LEFT)   left = true;
                        if (evt.key.keysym.sym ==  SDLK_RIGHT)  right = true;
                        if (evt.key.keysym.sym ==  SDLK_SPACE)  drop = true;
                        if (evt.key.keysym.sym ==  SDLK_ESCAPE) shouldRun = false;
                        break;
                    case SDL_WINDOWEVENT:
                        System.out.println("Window event " + evt.window.event);
                        break;
                    default:
                        break;
                }
            }

            java.time.LocalTime now = java.time.LocalTime.now();
            if (left && try_move_to(board, cur_type, cur_rot, cur_x - 1, cur_y))
                cur_x -= 1;
            if (right && try_move_to(board, cur_type, cur_rot, cur_x + 1, cur_y))
                cur_x += 1;

            if (rotate)
            {
                int old_rot = cur_rot;
                cur_rot = (cur_rot + 1) % block_max_rotations[cur_type - 1];

                if (try_move_to(board, cur_type, cur_rot, cur_x, cur_y)) {}
                else if (try_move_to(board, cur_type, cur_rot, cur_x + 1, cur_y)) { cur_x += 1; }
                else if (try_move_to(board, cur_type, cur_rot, cur_x - 1, cur_y)) { cur_x -= 1; }
                else if (cur_type == I && cur_x >= 8 && try_move_to(board, cur_type, cur_rot, cur_x - 2, cur_y)) { cur_x -= 2; }
                else if (try_move_to(board, cur_type, cur_rot, cur_x, cur_y - 1)) { cur_y -= 1; }
                else if (try_move_to(board, cur_type, cur_rot, cur_x, cur_y + 1)) { cur_y += 1; }
                else { cur_rot = old_rot; }
            }

            boolean glue = false;
            if (down || now.isAfter(next_down_time))
            {
                if (try_move_to(board, cur_type, cur_rot, cur_x, cur_y - 1))
                    cur_y -= 1;
                else
                    glue = true;
                next_down_time = LocalTime.now().plusNanos(1000 * 1000 * 1000 / (lines_cleared / 10 + 1));
                force_repaint = true;
            }

            if (drop)
            {
                while (try_move_to(board, cur_type, cur_rot, cur_x, cur_y - 1))
                    cur_y -= 1;
                glue = true;
            }

            if (glue)
            {
                Pos[] pos = get_block_positions(cur_type, cur_rot, cur_x, cur_y);
                for (Pos p : pos)
                    board[p.x][p.y] = cur_type;
                cur_type = (cur_type % 7) + 1;
                cur_x = 5;
                cur_y = 21;

                int removed = 0;
                for (int y = 0; y < 24; y++)
                {
                    int count = 0;
                    for (int x = 0; x < 10; x++) {
                        int tile = board[x][y];
                        if (tile != EMPTY)
                            count += 1;
                        board[x][y - removed] = tile;
                    }
                    if (count == 10)
                        removed += 1;
                }
                if (removed > 0) {
                    lines_cleared += removed;
                    score += (lines_cleared / 10 + 1) << removed;
                }
                System.out.println(score + " " + lines_cleared);
            }

            if (!force_repaint && !left && !right && !rotate && !down && !drop)
                continue;
            force_repaint = false;

            SDL_SetRenderDrawColor(renderer, (byte)20,(byte)100,(byte)70, (byte)255);
            SDL_RenderClear(renderer);

            IntByReference window_width = new IntByReference();
            IntByReference window_height = new IntByReference();
            SDL_GetWindowSize(window, window_width, window_height);

            int size = Math.min(window_width.getValue() / 10, window_height.getValue() / 23);
            int offset_x = (window_width.getValue() - size * 10) / 2;
            int offset_y = (window_height.getValue() - size * 23) / 2;

            SDL_Rect rect = new SDL_Rect();
            rect.w = size;
            rect.h = size;
            for (int x = 0; x < 10; x++)
            for (int y = 0; y < 24; y++)
            {
                if (board[x][y] == EMPTY)
                    continue;

                rect.x = offset_x + x * size;
                rect.y = offset_y + (22 - y) * size;

                byte[] c = get_color(board[x][y]);
                SDL_SetRenderDrawColor(renderer, c[0], c[1], c[2], (byte)255);
                SDL_RenderFillRect(renderer, rect);
            }

            SDL_SetRenderDrawColor(renderer, (byte)200,(byte)100,(byte)100, (byte)255);
            Pos[] pos = get_block_positions(cur_type, cur_rot, cur_x, cur_y);
            for (Pos p : pos)
            {
                rect.x = offset_x + p.x * size;
                rect.y = offset_y + (22 - p.y) * size;

                byte[] c = get_color(cur_type);
                SDL_SetRenderDrawColor(renderer, c[0], c[1], c[2], (byte)255);
                SDL_RenderFillRect(renderer, rect);
            }


            SDL_RenderDrawRect(renderer, rect);
            SDL_RenderPresent(renderer);
            System.out.println("flip");
        }

        SDL_Quit();
    }

    static final int EMPTY = 0;
    static final int L=1;
    static final int J=2;
    static final int Z=3;
    static final int S=4;
    static final int T=5;
    static final int I=6;
    static final int O=7;
    //                                        L         J         Z         S         T         I         O
    static final int[] block_offsets_x = {-1,-1, 1, -1, 1, 1, -1, 0, 1, -1, 0, 1, -1, 0, 1, -1, 1, 2,  0, 1, 1 };
    static final int[] block_offsets_y = {-1, 0, 0,  0, 0,-1,  0,-1,-1, -1,-1, 0,  0,-1, 0,  0, 0, 0, -1, 0,-1 };
    static final int[] block_max_rotations = {4,        4,        2,        2,        4,        2,        1};

    private static class Pos
    {
        int x; int y;
        Pos(int x, int y) { this.x = x; this.y = y; }
    }

    private static Pos[] get_block_positions(int type, int rot, int cur_x, int cur_y)
    {
        Pos[] result = new Pos[4];
        for (int i = 0; i < 4; i++)
        {
            int x = 0;
            int y = 0;
            if (i < 3)
            {
                int o = (type - 1) * 3 + i;
                x = block_offsets_x[o];
                y = block_offsets_y[o];
            }

            int rx = x;
            int ry = y;
            if (rot == 1) {
                rx = y;
                ry = -x;
            }
            else if (rot == 2) {
                rx = -x;
                ry = -y;
            }
            else if (rot == 3) {
                rx = -y;
                ry = x;
            }
            result[i] = new Pos(cur_x + rx, cur_y + ry);
        }
        return result;
    }

    public static boolean try_move_to(int[][] board, int type, int rot, int cur_x, int cur_y)
    {
        Pos[] pos = get_block_positions(type, rot, cur_x, cur_y);
        for (Pos p : pos)
            if (p.x < 0 || p.x > 9 || p.y < 0 || board[p.x][p.y] != EMPTY)
                return false;
        return true;
    }

    private static byte[] get_color(int type)
    {
        byte x = (byte)200;
        byte y = (byte)100;
        byte z = (byte)50;
        switch (type)
        {
            case L: return new byte[]{x, y, z};
            case J: return new byte[]{y, x, z};
            case Z: return new byte[]{x, z, y};
            case S: return new byte[]{z, y, x};
            case T: return new byte[]{y, z, x};
            case I: return new byte[]{z, x, y};
            case O: return new byte[]{x, x, z};
        }
        throw new IllegalArgumentException("bad piece type");
    }
}