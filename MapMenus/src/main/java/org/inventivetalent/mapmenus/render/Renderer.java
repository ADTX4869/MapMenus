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
import org.apache.commons.io.IOUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.inventivetalent.mapmanager.controller.MapController;
import org.inventivetalent.mapmanager.controller.MultiMapController;
import org.inventivetalent.mapmanager.manager.MapManager;
import org.inventivetalent.mapmanager.wrapper.MapWrapper;
import org.inventivetalent.mapmenus.Callback;
import org.inventivetalent.mapmenus.MapMenusPlugin;
import org.inventivetalent.mapmenus.TimingsHelper;
import org.inventivetalent.mapmenus.bounds.FixedBounds;
import org.inventivetalent.mapmenus.bounds.IBounds;
import org.inventivetalent.mapmenus.menu.ScriptMapMenu;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;
import sun.java2d.SunGraphics2D;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
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
		TimingsHelper.startTiming("MapMenu:renderer:render");
		renderable.render(this.imageGraphics, player);
		TimingsHelper.stopTiming("MapMenu:renderer:render");

		if (display) {
			TimingsHelper.startTiming("MapMenu:renderer:display");
			display(player);
			TimingsHelper.stopTiming("MapMenu:render:display");
		}

		// Reset image
		this.menuImage.flush();
		this.menuImage.rgbData = null;
		// Reset component translation
		this.imageGraphics.translate(0, 0);
		if (this.imageGraphics instanceof SunGraphics2D) {
			((SunGraphics2D) this.imageGraphics).transX=0;
			((SunGraphics2D) this.imageGraphics).transY = 0;
		}
	}

	public void refresh() {
		TimingsHelper.startTiming("MapMenu:renderer:refresh");
		frameContainer.refreshFrames();

		final int[][] frameIds = frameContainer.getItemFrameIds();
		for (Map.Entry<UUID, MapWrapper> entry : wrapperMap.entrySet()) {
			final Player player = Bukkit.getPlayer(entry.getKey());
			if (player == null) { continue; }
			((MultiMapController) entry.getValue().getController()).showInFrames(player, frameIds, new MultiMapController.DebugCallable() {
				@Override
				public String call(MapController mapController, int i, int i1) {
					return ((ScriptMapMenu) renderable).getHoverCallable().call(player, mapController, i, i1);
				}
			});
		}
		TimingsHelper.stopTiming("MapMenu:renderer:refresh");
	}

	public MapWrapper removeViewer(OfflinePlayer player) {
		MapWrapper wrapper = wrapperMap.remove(player.getUniqueId());
		if (wrapper != null) {
			wrapper.getController().clearViewers();
		}
		return wrapper;
	}

	public void display(final Player player) {
		MapWrapper mapWrapper = wrapperMap.get(player.getUniqueId());
		final MultiMapController[] mapController = new MultiMapController[1];
		if (mapWrapper == null) {
			TimingsHelper.startTiming("MapMenu:renderer:display:newWrapper");

			boolean prevCheckDuplicated = MapManager.Options.CHECK_DUPLICATES;
			MapManager.Options.CHECK_DUPLICATES = false;

			mapWrapper = MapMenusPlugin.instance.mapManager.wrapMultiImage(this.menuImage, ((ScriptMapMenu) renderable).getBlockHeight(), ((ScriptMapMenu) renderable).getBlockWidth());
			mapController[0] = (MultiMapController) mapWrapper.getController();

			mapController[0].addViewer(player);
			mapController[0].sendContent(player);

			wrapperMap.put(player.getUniqueId(), mapWrapper);

			MapManager.Options.CHECK_DUPLICATES = prevCheckDuplicated;

			TimingsHelper.stopTiming("MapMenu:renderer:display:newWrapper");
		} else {
			TimingsHelper.startTiming("MapMenu:renderer:display:update");
			mapController[0] = (MultiMapController) mapWrapper.getController();
			if (!menuImage.contentEqual((mapController[0].getContent()))) {
				mapController[0].update(menuImage);
			}
			TimingsHelper.stopTiming("MapMenu:renderer:display:update");
			//			mapController.sendContent(player);
		}
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

	public void downloadImageData(final String source, final Callback<String> callback) {
		Bukkit.getScheduler().runTaskAsynchronously(MapMenusPlugin.instance, new Runnable() {
			@Override
			public void run() {
				try {
					HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
					connection.setRequestProperty("User-Agent", "MapMenus/" + MapMenusPlugin.instance.getDescription().getVersion());
					String base64 = new String(Base64Coder.encode(IOUtils.toByteArray(connection.getInputStream())));
					callback.call(base64);
				} catch (Throwable throwable) {
					MapMenusPlugin.instance.getLogger().warning("Failed to download image '" + source + "'");
					callback.call("");
				}
			}
		});
	}

	public void drawImageData(Graphics2D graphics, String imageData, int x, int y, int width, int height) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64Coder.decode(imageData)));
			graphics.drawImage(image, x, y, width, height, null);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
