package crazypants.enderio.conduit.render;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.enderio.core.client.render.BoundingBox;
import com.enderio.core.client.render.CubeRenderer;
import com.enderio.core.client.render.IconUtil;
import com.enderio.core.client.render.RenderUtil;
import com.enderio.core.common.util.BlockCoord;
import com.enderio.core.common.util.IBlockAccessWrapper;
import com.google.common.collect.Lists;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import crazypants.enderio.EnderIO;
import crazypants.enderio.conduit.BlockConduitBundle;
import crazypants.enderio.conduit.ConduitDisplayMode;
import crazypants.enderio.conduit.ConduitUtil;
import crazypants.enderio.conduit.ConnectionMode;
import crazypants.enderio.conduit.IConduit;
import crazypants.enderio.conduit.IConduitBundle;
import crazypants.enderio.conduit.IConduitBundle.FacadeRenderState;
import crazypants.enderio.conduit.RaytraceResult;
import crazypants.enderio.conduit.TileConduitBundle;
import crazypants.enderio.conduit.facade.BlockConduitFacade;
import crazypants.enderio.conduit.geom.CollidableComponent;
import crazypants.enderio.conduit.geom.ConduitConnectorType;
import crazypants.enderio.conduit.geom.ConduitGeometryUtil;
import crazypants.enderio.config.Config;
import crazypants.enderio.machine.painter.PaintedBlockRenderer;

@SideOnly(Side.CLIENT)
public class ConduitBundleRenderer extends TileEntitySpecialRenderer implements ISimpleBlockRenderingHandler {

  public ConduitBundleRenderer(float conduitScale) {
  }

  @Override
  public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTick) {
    IConduitBundle bundle = (IConduitBundle) te;
    EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
    if(bundle.hasFacade() && bundle.getSourceBlock().isOpaqueCube() && !ConduitUtil.isFacadeHidden(bundle, player)) {
      return;
    }

    float brightness = -1;
    for (IConduit con : bundle.getConduits()) {
      if(ConduitUtil.renderConduit(player, con)) {
        ConduitRenderer renderer = EnderIO.proxy.getRendererForConduit(con);
        if(renderer.isDynamic()) {
          if(brightness == -1) {
            BlockCoord loc = bundle.getLocation();
            brightness = bundle.getEntity().getWorldObj().getLightBrightnessForSkyBlocks(loc.x, loc.y, loc.z, 0);

            RenderUtil.bindBlockTexture();

            GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
            GL11.glPushAttrib(GL11.GL_LIGHTING_BIT);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glShadeModel(GL11.GL_SMOOTH);

            GL11.glPushMatrix();
            GL11.glTranslated(x, y, z);

            Tessellator.instance.startDrawingQuads();
          }
          renderer.renderDynamicEntity(this, bundle, con, x, y, z, partialTick, brightness);

        }
      }
    }

    if(brightness != -1) {
      Tessellator.instance.draw();

      GL11.glShadeModel(GL11.GL_FLAT);
      GL11.glPopMatrix();
      GL11.glPopAttrib();
      GL11.glPopAttrib();
    }

  }

  @Override
  public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks rb) {
    boolean isBreakTexture = rb.hasOverrideBlockTexture() && rb.overrideBlockTexture.getIconName().startsWith("destroy_stage_");
    int pass = isBreakTexture ? 1 : MinecraftForgeClient.getRenderPass();

    //If the MC renderer is told that an alpha pass is required ( see BlockConduitBundle.getRenderBlockPass() ) put
    //nothing is actually added to the tessellator in this pass then the renderer will crash. We cant selectively
    //enable the alpha pass based on state so the only work around is to ensure we always render something in this
    //pass. Throwing in a polygon with a 0 area does the job
    //See: https://github.com/MinecraftForge/MinecraftForge/issues/981
    if(pass == 1) {
      Tessellator.instance.addVertexWithUV(x, y, z, 0, 0);
      Tessellator.instance.addVertexWithUV(x, y, z, 0, 0);
      Tessellator.instance.addVertexWithUV(x, y, z, 0, 0);
      Tessellator.instance.addVertexWithUV(x, y, z, 0, 0);
    }

    IConduitBundle bundle = (IConduitBundle) world.getTileEntity(x, y, z);
    EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;

    boolean renderedFacade = renderFacade(x, y, z, rb, pass, bundle, player);
    boolean renderConduit = !renderedFacade || ConduitUtil.isFacadeHidden(bundle, player);

    if(renderConduit && (pass == 0 || isBreakTexture)) {
      BlockCoord loc = bundle.getLocation();
      float brightness;
      if(!Config.updateLightingWhenHidingFacades && bundle.hasFacade() && ConduitUtil.isFacadeHidden(bundle, player)) {
        brightness = 15 << 20 | 15 << 4;
      } else {
        brightness = bundle.getEntity().getWorldObj().getLightBrightnessForSkyBlocks(loc.x, loc.y, loc.z, 0);
      }
      renderConduits(bundle, x, y, z, 0, brightness, rb);
      return true;
    }

    return renderedFacade || (bundle.hasFacade() && !bundle.getSourceBlock().isOpaqueCube());
  }
  
  private static final PaintedBlockRenderer renderer = new PaintedBlockRenderer(0, EnderIO.blockConduitFacade);

  private boolean renderFacade(int x, int y, int z, RenderBlocks rb, int pass, IConduitBundle bundle, EntityClientPlayerMP player) {
    boolean res = false;
    if(bundle.hasFacade()) {
      res = true;
      Block facadeId = bundle.getSourceBlock();
      BlockConduitFacade facb = EnderIO.blockConduitFacade;
      if(ConduitUtil.isFacadeHidden(bundle, player)) {
        Tessellator.instance.setColorOpaque_F(1, 1, 1);
        bundle.setSourceBlock(null, false);
        bundle.setFacadeRenderAs(FacadeRenderState.WIRE_FRAME);

        facb.setBlockOverride(bundle);
        facb.setBlockBounds(0, 0, 0, 1, 1, 1);
        if(!rb.hasOverrideBlockTexture()) {
          rb.setRenderBoundsFromBlock(facb);
          rb.renderStandardBlock(facb, x, y, z);
        }
        facb.setBlockOverride(null);
        bundle.setSourceBlock(facadeId, false);
      } else if(facadeId != null) {
        res = renderer.renderWorldBlock(player.worldObj, x, y, z, facb, 0, rb, pass);
      }
    } else {
      bundle.setFacadeRenderAs(FacadeRenderState.NONE);
    }
    return res;
  }

  public void renderConduits(IConduitBundle bundle, double x, double y, double z, float partialTick, float brightness, RenderBlocks rb) {

    Tessellator tessellator = Tessellator.instance;
    tessellator.setColorOpaque_F(1, 1, 1);
    tessellator.addTranslation((float) x, (float) y, (float) z);

    // Conduits
    Set<ForgeDirection> externals = new HashSet<ForgeDirection>();
    EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;

    List<BoundingBox> wireBounds = new ArrayList<BoundingBox>();

    for (IConduit con : bundle.getConduits()) {

      if(ConduitUtil.renderConduit(player, con)) {
        ConduitRenderer renderer = EnderIO.proxy.getRendererForConduit(con);
        renderer.renderEntity(this, bundle, con, x, y, z, partialTick, brightness, rb);
        Set<ForgeDirection> extCons = con.getExternalConnections();
        for (ForgeDirection dir : extCons) {
          if(con.getConnectionMode(dir) != ConnectionMode.DISABLED && con.getConnectionMode(dir) != ConnectionMode.NOT_SET) {
            externals.add(dir);
          }
        }
      } else if(con != null) {
        Collection<CollidableComponent> components = con.getCollidableComponents();
        for (CollidableComponent component : components) {
          wireBounds.add(component.bound);
        }
      }
    }

    // Internal conectors between conduits
    List<CollidableComponent> connectors = bundle.getConnectors();
    List<CollidableComponent> rendered = Lists.newArrayList();
    for (CollidableComponent component : connectors) {
      if(component.conduitType != null) {
        IConduit conduit = bundle.getConduit(component.conduitType);
        if(conduit != null) {
          if(ConduitUtil.renderConduit(player, component.conduitType)) {
            if(rb.hasOverrideBlockTexture()) {
              List<RaytraceResult> results = EnderIO.blockConduitBundle.doRayTraceAll(bundle.getWorld(), MathHelper.floor_double(x),
                  MathHelper.floor_double(y), MathHelper.floor_double(z), EnderIO.proxy.getClientPlayer());
              for (RaytraceResult r : results) {
                // the connectors can be rendered multiple times and this makes the break texture look funky
                if(r.component.conduitType == component.conduitType && !rendered.contains(r.component)) {
                  rendered.add(r.component);
                  CubeRenderer.render(component.bound, rb.overrideBlockTexture, true);
                }
              }
            } else {
              tessellator.setBrightness((int) (brightness));
              CubeRenderer.render(component.bound, conduit.getTextureForState(component), true);
            }
          } else {
            wireBounds.add(component.bound);
          }
        }

      } else if(ConduitUtil.getDisplayMode(player) == ConduitDisplayMode.ALL && !rb.hasOverrideBlockTexture()) {
        IIcon tex = EnderIO.blockConduitBundle.getConnectorIcon(component.data);
        CubeRenderer.render(component.bound, tex);
      }
    }
    //render these after the 'normal' conduits so help with proper blending
    for (BoundingBox wireBound : wireBounds) {
      Tessellator.instance.setColorRGBA_F(1, 1, 1, 0.25f);
      CubeRenderer.render(wireBound, EnderIO.blockConduitFacade.getIcon(0, 0));
    }

    Tessellator.instance.setColorRGBA_F(1, 1, 1, 1f);
    // External connection terminations
    if(rb.overrideBlockTexture == null) {
      for (ForgeDirection dir : externals) {
        renderExternalConnection(dir);
      }
    }
    tessellator.addTranslation(-(float) x, -(float) y, -(float) z);

  }

  private void renderExternalConnection(ForgeDirection dir) {
    IIcon tex = EnderIO.blockConduitBundle.getConnectorIcon(ConduitConnectorType.EXTERNAL);
    BoundingBox[] bbs = ConduitGeometryUtil.instance.getExternalConnectorBoundingBoxes(dir);
    for (BoundingBox bb : bbs) {
      CubeRenderer.render(bb, tex, true);
    }
  }

  @Override
  public boolean shouldRender3DInInventory(int modelId) {
    return false;
  }

  @Override
  public void renderInventoryBlock(Block block, int metadata, int modelID, RenderBlocks renderer) {
  }

  @Override
  public int getRenderId() {
    return BlockConduitBundle.rendererId;
  }
}
