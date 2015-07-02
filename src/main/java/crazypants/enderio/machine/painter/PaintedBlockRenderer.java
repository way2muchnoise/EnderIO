package crazypants.enderio.machine.painter;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;

import com.enderio.core.client.render.CubeRenderer;
import com.enderio.core.client.render.IconUtil;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

public class PaintedBlockRenderer implements ISimpleBlockRenderingHandler {

  private int renderId;
  private Block defaultBlock;

  public PaintedBlockRenderer(int renderId, Block defaultBlock) {
    this.renderId = renderId;
    this.defaultBlock = defaultBlock;
  }

  @Override
  public int getRenderId() {
    return renderId;
  }

  @Override
  public void renderInventoryBlock(Block blk, int meta, int modelId, RenderBlocks arg3) {
    Tessellator.instance.startDrawingQuads();
    CubeRenderer.render(blk, meta);
    Tessellator.instance.draw();
  }

  @Override
  public boolean renderWorldBlock(IBlockAccess ba, int x, int y, int z, Block block, int arg5, RenderBlocks rb) {
    return renderWorldBlock(ba, x, y, z, block, arg5, rb, PainterUtil.getPassForPaintedRender(rb));
  }
  
  public boolean renderWorldBlock(IBlockAccess ba, int x, int y, int z, Block block, int arg5, RenderBlocks rb, int pass) {
    boolean res = false;
    TileEntity tile = ba.getTileEntity(x, y, z);
    if (!(tile instanceof IPaintableTileEntity)) {
      return false;
    }
    IPaintableTileEntity te = (IPaintableTileEntity) tile;
    Block srcBlk = te.getSourceBlock();
    if (srcBlk == null) {
      srcBlk = defaultBlock;
    }

    IBlockAccess origBa = rb.blockAccess;
    res = true;
    boolean isFacadeOpaque = srcBlk.isOpaqueCube();

    if (((isFacadeOpaque || srcBlk.canRenderInPass(0)) && pass == 0) || ((!isFacadeOpaque || srcBlk.canRenderInPass(1)) && pass == 1)) {
      rb.blockAccess = new PaintedBlockAccessWrapper(origBa);
      try {
        rb.renderBlockByRenderType(srcBlk, x, y, z);
      } catch (Exception e) {
        //just in case the paint source wont render safely in this way
        rb.setOverrideBlockTexture(IconUtil.errorTexture);
        rb.renderStandardBlock(Blocks.stone, x, y, z);
        rb.setOverrideBlockTexture(null);
      }

      rb.blockAccess = origBa;
    }
    res = isFacadeOpaque;
    return res;
  }

  @Override
  public boolean shouldRender3DInInventory(int arg0) {
    return true;
  }

}
