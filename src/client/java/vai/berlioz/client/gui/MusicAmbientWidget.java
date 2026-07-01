package vai.berlioz.client.gui;

import dev.lambdaurora.spruceui.Position;
import dev.lambdaurora.spruceui.render.SpruceGuiGraphics;
import dev.lambdaurora.spruceui.widget.AbstractSpruceWidget;
import dev.lambdaurora.spruceui.widget.container.SpruceContainerWidget;
import dev.lambdaurora.spruceui.widget.text.SpruceTextFieldWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import vai.berlioz.client.music.loader.AmbientZoneData;
import vai.berlioz.client.music.loader.Playlist;
import vai.berlioz.client.network.BerliozClientNetwork;
import vai.berlioz.client.worldedit.WorldEditTracker;

public class MusicAmbientWidget extends SpruceContainerWidget {
    private static final int GAP = 8;
    private static final int CENTER_W = 300;
    private static final int TITLE_H = 20;
    private static final int BACK_H = 16;
    private static final int BTN_H = 18;
    private static final int FIELD_H = 16;
    private static final int ROW_GAP = 4;

    private static final Identifier ICON_PLAYLIST_ADD =
            Identifier.fromNamespaceAndPath("berlioz", "icon/playlist_add");
    private static final Identifier ICON_SETTINGS =
            Identifier.fromNamespaceAndPath("berlioz", "icon/settings");
    private static final Identifier ICON_TELEPORT =
            Identifier.fromNamespaceAndPath("berlioz", "icon/teleport");
    private static final Identifier ICON_EDIT =
            Identifier.fromNamespaceAndPath("berlioz", "icon/edit");

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public Runnable onOpenPlaylist = () -> { };
    public Runnable onOpenConfig = () -> { };
    public Consumer<AmbientZoneData> onSave = _ -> { };
    public Consumer<UUID> onDelete = _ -> { };
    public Consumer<AmbientZoneData> onTp = _ -> { };

    private boolean editing = false;
    private UUID editId = null;
    private Playlist working = new Playlist();

    private final List<AbstractSpruceWidget> editorWidgets = new ArrayList<>();
    private final SpruceTextFieldWidget nameField;
    private final SpruceTextFieldWidget[] pos1 = new SpruceTextFieldWidget[3];
    private final SpruceTextFieldWidget[] pos2 = new SpruceTextFieldWidget[3];
    private final SpruceTextFieldWidget urlField;
    private final TrackListWidget trackView;
    private final ScaledLabel hint;

    public MusicAmbientWidget(Position position, int width, int height) {
        super(position, width, height);

        int leftX = 0;
        int rightX = width - GuiStyle.PANEL_WIDTH;
        int centerX = (width - CENTER_W) / 2;
        int innerSide = GuiStyle.PANEL_WIDTH - GuiStyle.PADDING * 2;
        int innerCenter = CENTER_W - GuiStyle.PADDING * 2;

        int labelH = (int) Math.ceil(Minecraft.getInstance().font.lineHeight * GuiStyle.TEXT_SCALE);
        int cornerH = labelH + 2 + FIELD_H + ROW_GAP + FIELD_H + ROW_GAP * 2;
        int editorH = FIELD_H + ROW_GAP * 2 + cornerH * 2 + BTN_H + ROW_GAP * 2 + BTN_H;
        int centerH = editorH + GuiStyle.PADDING * 2;
        int centerTop = TITLE_H + GAP;

        this.addChild(new ScaledLabel(Position.of(this, 0, 6), width, true,
                Component.translatable("berlioz.ambient.title")));

        this.addChild(new Panel(Position.of(this, leftX, 0), GuiStyle.PANEL_WIDTH, height));
        int listH = height - GuiStyle.PADDING * 2 - BTN_H - ROW_GAP;
        this.addChild(new ZoneListView(
                Position.of(this, leftX + GuiStyle.PADDING, GuiStyle.PADDING), innerSide, listH));
        this.addChild(new WideButton(
                Position.of(this, leftX + GuiStyle.PADDING, height - GuiStyle.PADDING - BTN_H), innerSide, BTN_H,
                Component.translatable("berlioz.ambient.add"), this::beginCreate));

        this.addChild(new Panel(Position.of(this, rightX, 0), GuiStyle.PANEL_WIDTH, height));
        int gearW = BACK_H;
        int gearGap = 2;
        this.addChild(new WideButton(Position.of(this, rightX + GuiStyle.PADDING, GuiStyle.PADDING), gearW, BACK_H,
                Component.empty(), ICON_SETTINGS, () -> this.onOpenConfig.run()));
        this.addChild(new WideButton(
                Position.of(this, rightX + GuiStyle.PADDING + gearW + gearGap, GuiStyle.PADDING),
                innerSide - gearW - gearGap, BACK_H,
                Component.translatable("berlioz.playlist.title"), () -> this.onOpenPlaylist.run())
                .enabled(BerliozClientNetwork.hasPlaylistPermission()));
        int trackTop = GuiStyle.PADDING + BACK_H + ROW_GAP;
        int addBtnY = height - GuiStyle.PADDING - GuiStyle.ADD_BUTTON_H;
        int urlY = addBtnY - 2 - GuiStyle.URL_FIELD_H;
        int trackH = urlY - ROW_GAP - trackTop;
        this.trackView = new TrackListWidget(
                Position.of(this, rightX + GuiStyle.PADDING, trackTop), innerSide, trackH, false);
        addEditor(this.trackView);
        this.urlField = field(rightX + GuiStyle.PADDING, urlY, innerSide, GuiStyle.URL_FIELD_H,
                Component.translatable("berlioz.ambient.url"));
        addEditor(new WideButton(Position.of(this, rightX + GuiStyle.PADDING, addBtnY),
                innerSide, GuiStyle.ADD_BUTTON_H,
                Component.translatable("berlioz.playlist.add"), ICON_PLAYLIST_ADD,
                this::addTrackFromField));

        this.addChild(new Panel(Position.of(this, centerX, centerTop), CENTER_W, centerH));
        int cx = centerX + GuiStyle.PADDING;
        int y = centerTop + GuiStyle.PADDING;
        this.nameField = field(cx, y, innerCenter, Component.translatable("berlioz.ambient.name"));
        y += FIELD_H + ROW_GAP * 2;
        y = buildCorner(cx, y, innerCenter, Component.translatable("berlioz.ambient.pos1"), pos1);
        y = buildCorner(cx, y, innerCenter, Component.translatable("berlioz.ambient.pos2"), pos2);
        int weW = innerCenter / 2;
        addEditor(new WideButton(Position.of(this, cx + (innerCenter - weW) / 2, y), weW, BTN_H,
                Component.translatable("berlioz.ambient.fillWe"), this::fillFromWorldEdit));
        y += BTN_H + ROW_GAP * 2;
        int actW = (innerCenter - 2 * GAP) / 3;
        addEditor(new WideButton(Position.of(this, cx, y), actW, BTN_H,
                Component.translatable("berlioz.ambient.delete"), this::doDelete));
        addEditor(new WideButton(Position.of(this, cx + actW + GAP, y), actW, BTN_H,
                Component.translatable("berlioz.ambient.cancel"), this::endEdit));
        addEditor(new WideButton(Position.of(this, cx + (actW + GAP) * 2, y), actW, BTN_H,
                Component.translatable("berlioz.ambient.save"), this::doSave));
        this.hint = new ScaledLabel(Position.of(this, centerX, centerTop + centerH / 2 - labelH / 2),
                CENTER_W, true, Component.translatable("berlioz.ambient.hint"));
        this.addChild(this.hint);

        this.trackView.setPlaylist(this.working);
        applyEditing();
    }

    private void beginCreate() {
        this.editing = true;
        this.editId = null;
        this.working = new Playlist();
        this.trackView.setPlaylist(this.working);
        this.nameField.setText("");
        LocalPlayer p = Minecraft.getInstance().player;
        int px = p != null ? p.getBlockX() : 0;
        int py = p != null ? p.getBlockY() : 0;
        int pz = p != null ? p.getBlockZ() : 0;
        setCorner(pos1, px, py, pz);
        setCorner(pos2, px, py, pz);
        applyEditing();
    }

    private void beginEdit(AmbientZoneData zone) {
        this.editing = true;
        this.editId = zone.id();
        this.working = new Playlist();
        for (String url : zone.urls()) {
            this.working.addMusic(url);
        }
        this.trackView.setPlaylist(this.working);
        this.nameField.setText(zone.name());
        setCorner(pos1, zone.x1(), zone.y1(), zone.z1());
        setCorner(pos2, zone.x2(), zone.y2(), zone.z2());
        applyEditing();
    }

    private void endEdit() {
        this.editing = false;
        applyEditing();
    }

    private void doSave() {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < working.size(); i++) {
            Playlist.Music m = working.get(i);
            if (m != null) {
                urls.add(m.url());
            }
        }
        AmbientZoneData zone = new AmbientZoneData(editId, nameField.getText().trim(),
                intOf(pos1[0]), intOf(pos1[1]), intOf(pos1[2]),
                intOf(pos2[0]), intOf(pos2[1]), intOf(pos2[2]), urls);
        this.onSave.accept(zone);
        endEdit();
    }

    private void doDelete() {
        if (this.editId != null) {
            this.onDelete.accept(this.editId);
        }
        endEdit();
    }

    private void addTrackFromField() {
        String url = this.urlField.getText().trim();
        if (!url.isEmpty()) {
            this.working.addMusic(url);
            this.urlField.setText("");
        }
    }

    private void applyEditing() {
        for (AbstractSpruceWidget w : editorWidgets) {
            w.setVisible(this.editing);
        }
        this.hint.setVisible(!this.editing);
    }

    private int buildCorner(int x, int startY, int colW, Component label,
                            SpruceTextFieldWidget[] fields) {
        int y = startY;
        addEditor(new ScaledLabel(Position.of(this, x, y), colW, false, label));
        y += (int) Math.ceil(Minecraft.getInstance().font.lineHeight * GuiStyle.TEXT_SCALE) + 2;

        int fw = (colW - 2 * 2) / 3;
        for (int i = 0; i < 3; i++) {
            fields[i] = field(x + (fw + 2) * i, y, fw, Component.literal("xyz".substring(i, i + 1)));
            fields[i].setTextPredicate(MusicAmbientWidget::isIntInput);
        }
        y += FIELD_H + ROW_GAP;

        int bw = (colW - GAP) / 2;
        addEditor(new WideButton(Position.of(this, x, y), bw, FIELD_H,
                Component.translatable("berlioz.ambient.setPlayer"), () -> setFromPlayer(fields)));
        addEditor(new WideButton(Position.of(this, x + bw + GAP, y), bw, FIELD_H,
                Component.translatable("berlioz.ambient.setLook"), () -> setFromLook(fields)));
        return y + FIELD_H + ROW_GAP * 2;
    }

    private SpruceTextFieldWidget field(int x, int y, int w, Component title) {
        return field(x, y, w, FIELD_H, title);
    }

    private SpruceTextFieldWidget field(int x, int y, int w, int h, Component title) {
        SpruceTextFieldWidget f = new SpruceTextFieldWidget(Position.of(this, x, y), w, h, title);
        f.setRenderTextProvider((str, ignored) ->
                Component.literal(str).withStyle(s -> s.withFont(GuiStyle.FONT)).getVisualOrderText());
        addEditor(f);
        return f;
    }

    private void addEditor(AbstractSpruceWidget w) {
        editorWidgets.add(w);
        this.addChild(w);
    }

    private static void setFromPlayer(SpruceTextFieldWidget[] fields) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            setCorner(fields, p.getBlockX(), p.getBlockY(), p.getBlockZ());
        }
    }

    // Max distance (blocks) the "look" target can pick a block from
    private static final double LOOK_REACH = 128.0;

    private static void setFromLook(SpruceTextFieldWidget[] fields) {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.level == null) {
            return;
        }
        Vec3 eye = camera.getEyePosition(1.0f);
        Vec3 end = eye.add(camera.getViewVector(1.0f).scale(LOOK_REACH));
        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            setCorner(fields, pos.getX(), pos.getY(), pos.getZ());
        }
    }

    private void fillFromWorldEdit() {
        WorldEditTracker wet = WorldEditTracker.getInstance();
        if (wet.hasFirstPosition() && wet.hasSecondPosition()) {
            setCorner(this.pos1, wet.getFirstX(), wet.getFirstY(), wet.getFirstZ());
            setCorner(this.pos2, wet.getSecondX(), wet.getSecondY(), wet.getSecondZ());
        }
    }

    private static void setCorner(SpruceTextFieldWidget[] fields, int x, int y, int z) {
        fields[0].setText(Integer.toString(x));
        fields[1].setText(Integer.toString(y));
        fields[2].setText(Integer.toString(z));
    }

    private static boolean isIntInput(String s) {
        return s.isEmpty() || s.equals("-") || s.matches("-?\\d+");
    }

    private static int intOf(SpruceTextFieldWidget field) {
        String s = field.getText().trim();
        if (s.isEmpty() || s.equals("-")) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String arrowFor(double dx, double dz) {
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = ((int) Math.round(bearing / 45.0) % 8 + 8) % 8;
        return ARROWS[sector];
    }

    private static void drawBorder(SpruceGuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void drawText(SpruceGuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(GuiStyle.TEXT_SCALE, GuiStyle.TEXT_SCALE);
        g.text(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private static Component carlito(String s) {
        return Component.literal(s).withStyle(style -> style.withFont(GuiStyle.FONT));
    }

    private static final class Panel extends AbstractSpruceWidget {
        Panel(Position position, int w, int h) {
            super(position);
            this.width = w;
            this.height = h;
        }

        @Override
        protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            g.fill(x, y, x + w, y + h, GuiStyle.PANEL_BG);
            drawBorder(g, x, y, w, h, GuiStyle.PANEL_BORDER);
        }
    }

    private static final class ScaledLabel extends AbstractSpruceWidget {
        private final Component text;
        private final boolean centered;

        ScaledLabel(Position position, int w, boolean centered, Component text) {
            super(position);
            this.width = w;
            this.height = (int) Math.ceil(Minecraft.getInstance().font.lineHeight * GuiStyle.TEXT_SCALE);
            this.centered = centered;
            this.text = text.copy().withStyle(s -> s.withFont(GuiStyle.FONT));
        }

        @Override
        protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
            Font font = Minecraft.getInstance().font;
            int textW = font.width(this.text);
            int drawX = this.centered
                    ? this.getX() + (int) ((this.getWidth() - textW * GuiStyle.TEXT_SCALE) / 2f)
                    : this.getX();
            g.pose().pushMatrix();
            g.pose().translate(drawX, this.getY());
            g.pose().scale(GuiStyle.TEXT_SCALE, GuiStyle.TEXT_SCALE);
            g.text(font, this.text, 0, 0, this.centered ? GuiStyle.TEXT : GuiStyle.TEXT_DIM, this.centered);
            g.pose().popMatrix();
        }
    }

    private final class ZoneListView extends AbstractSpruceWidget {
        private static final int INSET = 3;
        private static final int ENTRY_H = 26;
        private static final int ENTRY_GAP = 2;
        private static final int MINI_W = 22;
        private static final int MINI_H = 11;
        private static final int SCROLL_STEP = 14;

        private int scrollPx = 0;

        ZoneListView(Position position, int w, int h) {
            super(position);
            this.width = w;
            this.height = h;
        }

        private int rowStride() {
            return ENTRY_H + ENTRY_GAP;
        }

        private int maxScroll(int n) {
            int contentH = Math.max(0, n * rowStride() - ENTRY_GAP);
            return Math.max(0, contentH - (this.getHeight() - INSET * 2));
        }

        @Override
        protected boolean onMouseScroll(double mx, double my, double sx, double sy) {
            int n = BerliozClientNetwork.getAmbientZones().size();
            this.scrollPx = Math.max(0, Math.min(maxScroll(n), this.scrollPx - (int) Math.signum(sy) * SCROLL_STEP));
            return true;
        }

        @Override
        protected boolean onMouseClick(MouseButtonEvent event, boolean dbl) {
            List<AmbientZoneData> zones = BerliozClientNetwork.getAmbientZones();
            int rel = (int) event.y() - (this.getY() + INSET) + this.scrollPx;
            if (rel < 0) return false;
            int index = rel / rowStride();
            int within = rel % rowStride();
            if (index >= zones.size() || within >= ENTRY_H) return false;
            AmbientZoneData zone = zones.get(index);
            int btnX = this.getX() + this.getWidth() - INSET - MINI_W;
            int mx = (int) event.x();
            if (mx >= btnX && mx < btnX + MINI_W) {
                if (within < MINI_H) {
                    MusicAmbientWidget.this.onTp.accept(zone);
                } else if (within >= MINI_H + 2 && within < MINI_H * 2 + 2) {
                    MusicAmbientWidget.this.beginEdit(zone);
                }
                return true;
            }
            return false;
        }

        @Override
        protected void extractWidgetRenderState(SpruceGuiGraphics g, int mx, int my, float d) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            g.fill(x, y, x + w, y + h, GuiStyle.LIST_BG);
            drawBorder(g, x, y, w, h, GuiStyle.LIST_BORDER);

            List<AmbientZoneData> zones = BerliozClientNetwork.getAmbientZones();
            LocalPlayer p = Minecraft.getInstance().player;
            Font font = Minecraft.getInstance().font;
            g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
            for (int i = 0; i < zones.size(); i++) {
                int entryY = y + INSET - this.scrollPx + i * rowStride();
                if (entryY + ENTRY_H <= y || entryY >= y + h) continue;
                drawEntry(g, font, zones.get(i), p, x + INSET, entryY, w - INSET * 2, mx, my);
            }
            g.disableScissor();
        }

        private void drawEntry(SpruceGuiGraphics g, Font font, AmbientZoneData zone, LocalPlayer p, int ex, int ey, int ew, int mx, int my) {
            boolean current = MusicAmbientWidget.this.editing && zone.id().equals(MusicAmbientWidget.this.editId);
            g.fill(ex, ey, ex + ew, ey + ENTRY_H, current ? GuiStyle.ENTRY_CURRENT : GuiStyle.ENTRY_BG);

            String name = zone.name().isEmpty() ? "?" : zone.name();
            drawText(g, font, carlito(name), ex + 2, ey + 2, current ? GuiStyle.TEXT_ACCENT : GuiStyle.TEXT);

            String dist = "--";
            String arrow = "";
            if (p != null) {
                double dx = zone.centerX() - p.getX();
                double dy = zone.centerY() - p.getY();
                double dz = zone.centerZ() - p.getZ();
                dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz) + "m";
                arrow = arrowFor(dx, dz);
            }
            g.pose().pushMatrix();
            g.pose().translate(ex + 2, ey + 14);
            g.pose().scale(GuiStyle.TEXT_SCALE, GuiStyle.TEXT_SCALE);
            g.text(font, Component.literal(arrow + " "), 0, 0, GuiStyle.TEXT_DIM, false);
            g.pose().popMatrix();
            drawText(g, font, carlito("  " + dist), ex + 2 + 8, ey + 14, GuiStyle.TEXT_DIM);

            int btnX = ex + ew - MINI_W;
            miniButton(g, ICON_TELEPORT, btnX, ey, mx, my);
            miniButton(g, ICON_EDIT, btnX, ey + MINI_H + 2, mx, my);
        }

        private void miniButton(SpruceGuiGraphics g, Identifier icon, int bx, int by, int mx, int my) {
            boolean hovered = mx >= bx && mx < bx + MINI_W && my >= by && my < by + MINI_H;
            g.fill(bx, by, bx + MINI_W, by + MINI_H, hovered ? GuiStyle.BTN_HOVER : GuiStyle.BTN_BG);
            drawBorder(g, bx, by, MINI_W, MINI_H, GuiStyle.BTN_BORDER);
            int iconSize = MINI_H - 2;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, icon,
                    bx + (MINI_W - iconSize) / 2, by + 1, iconSize, iconSize);
        }
    }

}
