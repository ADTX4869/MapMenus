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

package org.inventivetalent.mapmenus.menu;

import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.inventivetalent.mapmanager.controller.MapController;
import org.inventivetalent.mapmenus.MapMenusPlugin;
import org.inventivetalent.mapmenus.MenuScriptExecutionException;
import org.inventivetalent.mapmenus.TimingsHelper;
import org.inventivetalent.mapmenus.bounds.FixedBounds;
import org.inventivetalent.mapmenus.component.MenuComponentAbstract;
import org.inventivetalent.mapmenus.component.ScriptComponent;
import org.inventivetalent.mapmenus.render.IFrameContainer;
import org.inventivetalent.mapmenus.render.Renderer;
import org.inventivetalent.mapmenus.script.IScriptContainer;
import org.inventivetalent.mapmenus.script.ScriptManagerAbstract;
import org.inventivetalent.scriptconfig.NoSuchFunctionException;
import org.inventivetalent.scriptconfig.RuntimeScriptException;
import org.inventivetalent.scriptconfig.api.ScriptConfig;
import org.inventivetalent.vectors.d2.Vector2DDouble;
import org.inventivetalent.vectors.d3.Vector3DDouble;

import java.awt.*;
import java.util.UUID;
import java.util.logging.Level;

@EqualsAndHashCode(callSuper = true,
				   doNotUseGetters = true,
				   exclude = { "menu" })
@ToString(callSuper = true,
		  doNotUseGetters = true,
		  exclude = { "menu" })
public class ScriptMapMenu extends MapMenuAbstract implements IFrameContainer, IScriptContainer {

	//	private static final Executor SCRIPT_TICK_EXECUTOR = Executors.newSingleThreadExecutor();
	static final int[][] NULL_INT_ARRAY = new int[0][0];

	@Expose private final String name;
	public Renderer renderer = new Renderer(this, bounds, this);

	protected HoverCallable hoverCallable;

	@Expose private String         scriptName;
	private         ScriptConfig   script;
	public          BukkitRunnable scriptTask;

	boolean noTickFunction;
	boolean noRenderFunction;
	boolean noClickFunction;

	protected int[][] itemFrameIds = NULL_INT_ARRAY;
	@Expose protected UUID[][] itemFrameUUIDs;

	// Script references
	public         ScriptMapMenu    menu    = this;
	@Expose public ScriptOptions    options = new ScriptOptions();
	@Expose public ScriptMenuData   data    = new ScriptMenuData(new JsonObject());
	@Expose public ScriptMenuStates states  = new ScriptMenuStates();

	public ScriptMapMenu(@NonNull ItemFrame baseFrame, @NonNull Vector3DDouble firstCorner, @NonNull Vector3DDouble secondCorner, @NonNull String name) {
		super(baseFrame, firstCorner, secondCorner);
		this.name = name;
	}

	@Override
	public void setScript(String scriptName) {
		this.scriptName = scriptName;
	}

	@Override
	public void reloadScript() {
		// Reload the menu script
		reloadScript(MapMenusPlugin.instance.menuScriptManager);

		// Reload component scripts
		for (MenuComponentAbstract component : getComponents()) {
			if (component instanceof ScriptComponent) {
				((ScriptComponent) component).menu = this;
			}
			if (component instanceof IScriptContainer) {
				((IScriptContainer) component).reloadScript();
			}
		}
	}

	/**
	 * Reloads this menu's script file from the given ScriptManager
	 */
	public void reloadScript(ScriptManagerAbstract scriptManager) {
		if (this.scriptName == null || this.scriptName.isEmpty()) {
			throw new IllegalStateException("Script not yet set");
		}
		if (!scriptManager.doesScriptExist(this.scriptName)) {
			MapMenusPlugin.instance.getLogger().warning("Menu Script '" + script + "' does not exist (any longer)");
			return;
		}
		// Cancel the current task
		if (scriptTask != null) {
			scriptTask.cancel();
			scriptTask = null;
		}
		// Wrap the script
		this.script = scriptManager.wrapScript(this.scriptName);

		this.script.setVariable("menu", this);
		this.script.setVariable("renderer", this.renderer);
		this.script.setVariable("options", this.options);
		this.script.setVariable("data", this.data);
		this.script.setVariable("states", this.states);
		hoverCallable = new HoverCallable() {
			protected boolean noHoverTextFunction;

			@Override
			public String call(Player player, MapController mapController, int x, int y) {
				if (!noHoverTextFunction) {
					try {
						Object hoverText = ScriptMapMenu.this.script.callFunction("hoverText", player, x, y);
						return hoverText == null ? null : String.valueOf(hoverText);
					} catch (NoSuchFunctionException e) {
						// Ignore
						noHoverTextFunction = true;
					} catch (RuntimeScriptException e) {
						MapMenusPlugin.instance.getLogger().log(Level.WARNING, "Unexpected ScriptException whilst calling hoverText(): " + e.getException().getMessage(), e);
					}
				}
				return null;
			}
		};

		try {
			this.script.callFunction("init");
		} catch (NoSuchFunctionException e) {
			// Ignore
		} catch (RuntimeScriptException e) {
			MapMenusPlugin.instance.getLogger().log(Level.WARNING, "Unexpected ScriptException whilst calling init(): " + e.getException().getMessage(), e);
		}

		(scriptTask = new BukkitRunnable() {
			@Override
			public void run() {
				TimingsHelper.startTiming("MapMenu - tick");

				try {
					tick();
				} catch (MenuScriptExecutionException e) {
					cancel();
					TimingsHelper.stopTiming("MapMenu - tick");
					throw e;
				}

				TimingsHelper.stopTiming("MapMenu - tick");
			}
		}).runTaskTimer(MapMenusPlugin.instance, options.tickSpeed, options.tickSpeed);
	}

	public ScriptComponent addComponent(String script, int x, int y, int width, int height) {
		return addComponent(script, x, y, width, height, new Object[0]);
	}

	public ScriptComponent addComponent(String script, int x, int y, int width, int height, Object... initArgs) {
		return addComponent(new FixedBounds(x, y, width, height), script, initArgs);
	}

	public ScriptComponent addComponent(FixedBounds bounds, String scriptName, Object[] initArgs) {
		if (!MapMenusPlugin.instance.componentScriptManager.doesScriptExist(scriptName)) {
			MapMenusPlugin.instance.getLogger().warning("Component Script '" + scriptName + "' does not exist");
			return null;
		}

		tickLocked = true;

		ScriptComponent component = new ScriptComponent(this.bounds, bounds, scriptName, initArgs);
		component.menu = this;
		components.put(component.getUuid(), component);
		component.reloadScript();

		tickLocked = false;
		return component;
	}

	public CursorPosition getCursorPosition(Player player) {
		return CursorPosition.calculateFor(player, this);
	}

	@Override
	public void tick() {
		if (tickLocked) { return; }

		if (!noTickFunction) {
			try {
				script.callFunction("tick");
			} catch (NoSuchFunctionException e) {
				// Ignore this
				noTickFunction = true;
			} catch (RuntimeScriptException e) {
				MapMenusPlugin.instance.getLogger().log(Level.WARNING, "Unexpected ScriptException whilst calling tick(): " + e.getException().getMessage(), e);
				throw new MenuScriptExecutionException("ScriptException in Menu tick()", e);
			}
		}

		for (MenuComponentAbstract component : getComponents()) {
			component.tick();
		}
		//				throw new RuntimeScriptException(new Exception());
	}

	@Override
	public void render(Graphics2D graphics, Player player) {
		if (!noRenderFunction) {
			try {
				script.callFunction("render", graphics, player);
			} catch (NoSuchFunctionException e) {
				// Ignore this
				noRenderFunction = true;
			} catch (RuntimeScriptException e) {
				MapMenusPlugin.instance.getLogger().log(Level.WARNING, "Unexpected ScriptException whilst calling render(): " + e.getException().getMessage(), e);
				throw new MenuScriptExecutionException("ScriptException in Menu render()", e);
			}
		}

		for (MenuComponentAbstract component : getComponents()) {
			component.render(graphics, player);
		}
	}

	@Override
	public boolean click(Player player, CursorPosition absolutePosition, int action) {
		boolean clickHandled = false;

		if (!noClickFunction) {
			try {
				Object result = script.callFunction("click", player, absolutePosition, action);
				clickHandled = result != null;
			} catch (NoSuchFunctionException e) {
				// Ignore this
				noClickFunction = true;
			} catch (RuntimeScriptException e) {
				MapMenusPlugin.instance.getLogger().log(Level.WARNING, "Unexpected ScriptException whilst calling click(): " + e.getException().getMessage(), e);
				throw new MenuScriptExecutionException("ScriptException in Menu click()", e);
			}
		}

		for (MenuComponentAbstract component : getComponents()) {
			if (component.click(player, absolutePosition, action)) { clickHandled = true; }
		}
		return clickHandled;
	}

	public HoverCallable getHoverCallable() {
		return hoverCallable;
	}

	@Override
	public void refreshFrames() {
		if (!MapMenusPlugin.instance.isEnabled()) { return; }
		Bukkit.getScheduler().runTask(MapMenusPlugin.instance, new Runnable() {
			@Override
			public void run() {
				TimingsHelper.startTiming("MapMenu - refreshItemFrames");

				if(getWorld().getPlayers().isEmpty()){
					itemFrameIds = NULL_INT_ARRAY;
				}else {
					itemFrameIds = new int[getBlockWidth()][getBlockHeight()];
					itemFrameUUIDs = new UUID[getBlockWidth()][getBlockHeight()];

					//				Vector startVector = new Vector(boundingBox.minX, boundingBox.minY, boundingBox.minZ);
					Vector2DDouble startVector = minCorner2d;

					for (Entity entity : getWorld().getEntitiesByClass(ItemFrame.class)) {
						if (entity instanceof ItemFrame) {
							if (boundingBox.contains(new Vector3DDouble(entity.getLocation()))) {
							for (int y = 0; y < getBlockHeight(); y++) {
								for (int x1 = 0; x1 < getBlockWidth(); x1++) {
										int x = facing.isFrameModInverted() ? (getBlockWidth() - 1 - x1) : x1;
										Vector3DDouble vector3d = facing.getPlane().to3D(startVector.add(x, y), baseVector.getX(), baseVector.getZ());
										if (entity.getLocation().getBlockZ() == vector3d.getZ().intValue()) {
											if (entity.getLocation().getBlockX() == vector3d.getX().intValue()) {
												if (entity.getLocation().getBlockY() == vector3d.getY().intValue()) {
													itemFrameIds[x1][y] = entity.getEntityId();
													itemFrameUUIDs[x1][y] = entity.getUniqueId();
												}
											}
										}
									}
								}
							}
						}
					}

					TimingsHelper.stopTiming("MapMenu - refreshItemFrames");
				}
			}
		});
	}

	@Override
	public World getWorld() {
		return super.getWorld();
	}

	@Override
	public int[][] getItemFrameIds() {
		//		refreshItemFrames();
		return itemFrameIds;
	}

	public String getName() {
		return name;
	}
}