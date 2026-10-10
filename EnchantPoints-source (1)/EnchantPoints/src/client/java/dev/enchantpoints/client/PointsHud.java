package dev.enchantpoints.client;

import dev.enchantpoints.client.mixin.HandledScreenAccessor;
import dev.enchantpoints.net.PointsSyncPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Draws the counter in a small box attached to the inventory panel. Vanilla status effects are drawn to the
 * right of the panel and the recipe book to the left, so the box sits directly above the panel (or below it if
 * the window is too short), centred, and never overlaps slots or tooltips.
 */
final class PointsHud {
    private static volatile PointsSyncPayload state;   // null until the server has told us (mod not on server => no HUD)

    static void update(PointsSyncPayload p) { state = p; }
    static void clear() { state = null; }

    static void render(Screen screen, DrawContext ctx) {
        PointsSyncPayload s = state;
        if (s == null || !s.showHud() || !(screen instanceof HandledScreenAccessor acc)) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        int total = s.total(), max = s.max();
        boolean over = total > max;
        int color;
        if (over || (max > 0 && total >= max)) color = 0xFFFF5555;                       // red: at or over the limit
        else if (max > 0 && total * 100L >= (long) max * s.yellowAtPercent()) color = 0xFFFFFF55;   // yellow: approaching
        else color = 0xFF55FF55;                                                          // green

        Text title = Text.literal("Enchant Points");
        Text value = Text.literal(total + " / " + max);
        Text extra = over ? Text.literal((total - max) + " OVER") : null;

        int w = Math.max(tr.getWidth(title), Math.max(tr.getWidth(value), extra == null ? 0 : tr.getWidth(extra))) + 10;
        int lines = extra == null ? 2 : 3;
        int h = lines * 10 + 6;

        int px = acc.enchantpoints$getX(), py = acc.enchantpoints$getY(), pw = acc.enchantpoints$getBackgroundWidth();
        int ph = acc.enchantpoints$getBackgroundHeight();
        int x = px + (pw - w) / 2;
        int y = py - h - 2;
        if (y < 2) y = Math.min(py + ph + 2, screen.height - h - 2);   // no room above: go below
        x = Math.max(2, Math.min(x, screen.width - w - 2));

        ctx.fill(x, y, x + w, y + h, 0xC0101010);
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);

        int cy = y + 4;
        ctx.drawText(tr, title, x + (w - tr.getWidth(title)) / 2, cy, 0xFFFFFFFF, true);
        ctx.drawText(tr, value, x + (w - tr.getWidth(value)) / 2, cy + 10, color, true);
        if (extra != null) ctx.drawText(tr, extra, x + (w - tr.getWidth(extra)) / 2, cy + 20, 0xFFFF5555, true);
    }
}
