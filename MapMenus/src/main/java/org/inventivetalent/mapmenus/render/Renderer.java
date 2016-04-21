/*
 * Copyright 2016 inventivetalent. All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification, are
 *  permitted provided that the following conditions are met:
 *
 *     1. Redistributions of source code must retain the above copyright notice, this list of
 *        conditions and the following disclaimer.
 *
 *     2. Redistributions in binary form must reproduce the above copyright notice, this list
 *        of conditions and the following disclaimer in the documentation and/or other materials
 *        provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 *  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  The views and conclusions contained in the software and documentation are those of the
 *  authors and contributors and should not be interpreted as representing official policies,
 *  either expressed or implied, of anybody else.
 */

package org.inventivetalent.mapmenus.render;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.inventivetalent.mapmanager.controller.MapController;
import org.inventivetalent.mapmanager.controller.MultiMapController;
import org.inventivetalent.mapmanager.manager.MapManager;
import org.inventivetalent.mapmanager.wrapper.MapWrapper;
import org.inventivetalent.mapmenus.MapMenusPlugin;
import org.inventivetalent.mapmenus.bounds.FixedBounds;
import org.inventivetalent.mapmenus.bounds.IBounds;
import org.inventivetalent.mapmenus.menu.ScriptMapMenu;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EqualsAndHashCode(exclude = {
		"renderable",
		"bounds",
		"frameContainer" })
@ToString(exclude = {
		"renderable",
		"bounds",
		"frameContainer",
		"menuImage",
		"imageGraphics" })
public class Renderer {

	private final IRenderable     renderable;
	private final IBounds         bounds;
	private final IFrameContainer frameContainer;

	private final MenuImage  menuImage;
	private       Graphics2D imageGraphics;

	private final Map<UUID, MapWrapper> wrapperMap = new HashMap<>();

	//	private MapWrapper         mapWrapper;
	//	private MultiMapController mapController;

	public Renderer(@NonNull IRenderable renderable, @NonNull IBounds bounds, @NonNull IFrameContainer frameContainer) {
		this.renderable = renderable;
		this.bounds = bounds;
		this.frameContainer = frameContainer;

		this.menuImage = new MenuImage(bounds.getWidth(), bounds.getHeight(), BufferedImage.TYPE_BYTE_INDEXED);
		this.imageGraphics = menuImage.createGraphics();
	}

	public void render() {
		for (Player player : frameContainer.getWorld().getPlayers()) {
			render(player);
		}
	}

	public void render(Player player) {
		render(player, true);
	}

	public void render(Player player, boolean display) {
		// Render the actual menu
		renderable.render(this.imageGraphics, player);

		if (display) {
			display(player);
		}

		// Reset image
		this.menuImage.flush();
	}

	public void refresh() {
		frameContainer.refreshFrames();
	}

	public MapWrapper removeViewer(OfflinePlayer player) {
		MapWrapper wrapper = wrapperMap.remove(player.getUniqueId());
		if (wrapper != null) {
			wrapper.getController().removeViewer(player);
		}
		return wrapper;
	}

	public void display(final Player player) {
		MapWrapper mapWrapper = wrapperMap.get(player.getUniqueId());
		MultiMapController mapController;
		if (mapWrapper == null) {
			boolean prevCheckDuplicated = MapManager.Options.CHECK_DUPLICATES;
			MapManager.Options.CHECK_DUPLICATES = false;

			mapWrapper = MapMenusPlugin.instance.mapManager.wrapMultiImage(this.menuImage, ((ScriptMapMenu) renderable).getBlockHeight(), ((ScriptMapMenu) renderable).getBlockWidth());
			mapController = (MultiMapController) mapWrapper.getController();

			mapController.addViewer(player);
			mapController.sendContent(player);

			wrapperMap.put(player.getUniqueId(), mapWrapper);

			MapManager.Options.CHECK_DUPLICATES = prevCheckDuplicated;
		} else {
			mapController = (MultiMapController) mapWrapper.getController();
			mapController.update(this.menuImage);
			//			mapController.sendContent(player);
		}

		int[][] frameIds = frameContainer.getItemFrameIds();
		mapController.showInFrames(player, frameIds, new MultiMapController.DebugCallable() {
			@Override
			public String call(MapController mapController, int i, int i1) {
				return ((ScriptMapMenu) renderable).getHoverCallable().call(player, mapController, i, i1);
			}
		});
	}

	// Util render methods

	public void drawStringCentered(Graphics graphics, int x, int y, int width, int height, String text) {
		FontMetrics metrics = graphics.getFontMetrics(graphics.getFont());
		x += (width - metrics.stringWidth(text)) / 2;
		y += ((height - metrics.getHeight()) / 2) + metrics.getAscent();
		graphics.drawString(text, x, y);
	}

	public void drawStringCentered(Graphics graphics, FixedBounds bounds, String text) {
		drawStringCentered(graphics, bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), text);
	}

	public void drawStringCentered(Graphics graphics, FixedBounds bounds, String text, int xOffset, int yOffset) {
		drawStringCentered(graphics, bounds.getX() + xOffset, bounds.getY() + yOffset, bounds.getWidth(), bounds.getHeight(), text);
	}

}
