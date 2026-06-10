package com.ladderstar.adq.client;

import com.ladderstar.adq.QuestModel;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class FlightManualScreen extends AbstractSimiScreen {

    private final List<QuestModel> savedQuests;
    private final long savedCooldown;
    private final long savedNextQuestTimer;
    private int leftPos;
    private int topPos;

    public FlightManualScreen(List<QuestModel> savedQuests, long savedCooldown, long savedNextQuestTimer) {
        this.savedQuests = savedQuests;
        this.savedCooldown = savedCooldown;
        this.savedNextQuestTimer = savedNextQuestTimer;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - AllGuiTextures.CLIPBOARD.getWidth()) / 2;
        this.topPos = (this.height - AllGuiTextures.CLIPBOARD.getHeight()) / 2;

        // Custom Create back button (I_CONFIG_PREV)
        IconButton backBtn = new IconButton(leftPos + 113, topPos + 220, AllIcons.I_CONFIG_PREV);
        backBtn.setToolTip(Component.literal("Back to Quest Ledger"));
        this.addRenderableWidget(backBtn.withCallback(() -> {
            Minecraft.getInstance().setScreen(new QuestBoardScreen(savedQuests, savedCooldown, savedNextQuestTimer));
        }));
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Render the wooden clipboard texture
        AllGuiTextures.CLIPBOARD.render(graphics, leftPos, topPos);

        int inkColor = 0xFF5B453A; // Create's ink brown color
        int titleColor = 0xFF005A9C; // Deep blue for section headers

        // Header Title (Lowered to clear the clip, no shadow)
        String titleStr = "FLIGHT MANUAL";
        graphics.drawString(this.font, titleStr, leftPos + 122 - this.font.width(titleStr) / 2, topPos + 28, inkColor, false);

        int textY = topPos + 44;
        int wrapWidth = 160;

        graphics.drawString(this.font, "§lDelivery Operations", leftPos + 42, textY, titleColor, false);
        textY += 12;

        String description = "Accept a contract from the ledger to begin. Your compass will guide you to the pickup location, where the cargo carriage will spawn in a flat area.\n\n"
                + "Secure the cargo to your airship using ropes, chains, or docking couplers. Transport it safely to the destination coordinates.\n\n"
                + "Keep cargo intact! Block damage reduces your final payout.";

        for (String paragraph : description.split("\n\n")) {
            textY = drawWrappedText(graphics, paragraph, leftPos + 42, textY, wrapWidth, inkColor);
            textY += 6;
        }
    }

    private int drawWrappedText(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        List<String> lines = wrapText(text, maxWidth);
        for (String line : lines) {
            graphics.drawString(this.font, "§8" + line, x, y, color, false);
            y += 9;
        }
        return y;
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (this.font.width(testLine) > maxWidth) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(testLine);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
