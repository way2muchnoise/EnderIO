package crazypants.enderio.machine.painter;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

public class PaintedCarpetRenderer extends PaintedBlockRenderer {

  public PaintedCarpetRenderer(int renderId, Block block) {
    super(renderId, block);
  }
  
  @Override
  public boolean renderWorldBlock(IBlockAccess ba, int x, int y, int z, Block block, int arg5, RenderBlocks rb, int pass) {
    rb.setRenderBoundsFromBlock(block);
    rb.lockBlockBounds = true;
    boolean ret = super.renderWorldBlock(ba, x, y, z, block, arg5, rb, pass);
    rb.lockBlockBounds = false;
    return ret;
  }

}
