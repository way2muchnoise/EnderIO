package crazypants.enderio.conduit.gui;

import java.awt.Rectangle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.enderio.core.api.client.gui.IGuiScreen;
import com.enderio.core.client.gui.GuiScreenBase;
import com.enderio.core.client.render.RenderUtil;
import com.enderio.core.common.util.BlockCoord;
import com.enderio.core.common.vecmath.Vector4f;
import com.google.common.collect.Lists;

import crazypants.enderio.EnderIO;
import crazypants.enderio.GuiHandler;
import crazypants.enderio.conduit.IConduit;
import crazypants.enderio.conduit.IConduitBundle;
import crazypants.enderio.conduit.redstone.IInsulatedRedstoneConduit;
import crazypants.enderio.gui.IoConfigRenderer;
import crazypants.enderio.machine.gui.GuiOverlayIoConfig;
import crazypants.enderio.network.PacketHandler;

public class GuiExternalConnectionSelector extends GuiScreenBase {

  private static List<BlockCoord> getSurrounding(BlockCoord bc) {
    List<BlockCoord> ret = Lists.newArrayList();
    for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
      ret.add(bc.getLocation(dir));
    }
//    ret.add(bc);
    return ret;
  }
  
  private class GuiOverlaySelector extends GuiOverlayIoConfig {

    public GuiOverlaySelector(BlockCoord bc) {
      super(getSurrounding(bc));
      this.height = GuiExternalConnectionSelector.this.height - 100;
    }

    @Override
    public void init(IGuiScreen screen) {
      super.init(screen);
      renderer = new SelectorRenderer(coords);
      bounds = new Rectangle(0, 0, GuiExternalConnectionSelector.this.width, GuiExternalConnectionSelector.this.height);
    }
    
    @Override
    public void draw(int mouseX, int mouseY, float partialTick) {
      GL11.glEnable(GL11.GL_BLEND);
      RenderUtil.renderQuad2D(bounds.x, bounds.y, 0, bounds.width, bounds.height, new Vector4f(0, 0, 0, 0.3f));
      Minecraft mc = Minecraft.getMinecraft();
      ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
      
      int w = bounds.width * scaledresolution.getScaleFactor();
      int h = bounds.height * scaledresolution.getScaleFactor();
      
      renderer.drawScreen(mouseX, mouseY, partialTick, new Rectangle(0, 0, w, h), bounds);
    }
  }

  private static class SelectorRenderer extends IoConfigRenderer {

    public SelectorRenderer(List<BlockCoord> coords) {
      super(coords);
      renderNeighbours = false;
    }
  }

  Set<ForgeDirection> cons;
  IConduitBundle cb;

  private GuiOverlayIoConfig configOverlay;

  public GuiExternalConnectionSelector(IConduitBundle cb) {
    this.cb = cb;
    cons = new HashSet<ForgeDirection>();
    for (IConduit con : cb.getConduits()) {
      if (con instanceof IInsulatedRedstoneConduit) {
        Set<ForgeDirection> conCons = con.getConduitConnections();
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
          if (!conCons.contains(dir)) {
            cons.add(dir);
          }
        }
      } else {
        cons.addAll(con.getExternalConnections());
      }
    }
  }
  
  @Override
  protected void mouseClicked(int x, int y, int b) {
    super.mouseClicked(x, y, b);
    configOverlay.handleMouseInput(x, y, b);
  }
  
  @Override
  public void handleMouseInput() {
    super.handleMouseInput();
    int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
    int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
    int b = Mouse.getEventButton();
    configOverlay.handleMouseInput(x, y, b);
  }
  
  @Override
  protected void actionPerformed(GuiButton b) {
    ForgeDirection dir = ForgeDirection.values()[b.id];
    EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
    BlockCoord loc = cb.getLocation();
    PacketHandler.INSTANCE.sendToServer(new PacketOpenConduitUI(cb.getEntity(), dir));
    player.openGui(EnderIO.instance, GuiHandler.GUI_ID_EXTERNAL_CONNECTION_BASE + dir.ordinal(), player.worldObj, loc.x, loc.y, loc.z);
  }

  @Override
  public void initGui() {
    super.initGui();
    configOverlay = new GuiOverlaySelector(cb.getLocation());
    configOverlay.init(this);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }

  @Override
  protected void drawBackgroundLayer(float par3, int par1, int par2) {
    GL11.glPushMatrix();
    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    GL11.glDisable(GL11.GL_DEPTH_TEST);
//    GL11.glTranslatef(guiLeft, guiTop, 0.0F);
    configOverlay.draw(par1, par2, par3);
    GL11.glEnable(GL11.GL_DEPTH_TEST);
    GL11.glPopMatrix();
  }
}
