package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/** Version-pinned native drawing, hit testing and selection all share one visible page. */
public final class CodaCreativePagesTransformer implements ClassFileTransformer {
    private static final String SCREEN=CodaCreativeInventoryTransformer.SCREEN;
    private static final String TAB="net/minecraft/world/item/CreativeModeTab";
    private static final String ROW=TAB+"$Row";
    private static final String HOOK="dev/howlingwhispers/codaloader/bootstrap/CodaCreativeTabPages";
    static void install(Instrumentation instrumentation){instrumentation.addTransformer(new CodaCreativePagesTransformer(),false);}
    @Override public byte[] transform(ClassLoader loader,String name,Class<?> redefining,ProtectionDomain domain,byte[] bytes){
        if(!SCREEN.equals(name))return null;
        try {
            ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
            int[] hooks=new int[5];
            reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions){
                    MethodVisitor mv=super.visitMethod(access,method,desc,signature,exceptions);
                    return new MethodVisitor(Opcodes.ASM9,mv){
                        @Override public void visitCode(){
                            super.visitCode();
                            if(method.equals("selectTab")&&desc.equals("(L"+TAB+";)V")){
                                super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,HOOK,"selected","(Ljava/lang/Object;)V",false);hooks[3]++;
                            }
                        }
                        @Override public void visitInsn(int opcode){
                            if(method.equals("init")&&desc.equals("()V")&&opcode==Opcodes.RETURN){
                                super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,HOOK,"initialize","(Ljava/lang/Object;)V",false);hooks[2]++;
                            }
                            if(method.equals("removed")&&desc.equals("()V")&&opcode==Opcodes.RETURN){
                                super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,HOOK,"removed","(Ljava/lang/Object;)V",false);hooks[4]++;
                            }
                            super.visitInsn(opcode);
                        }
                        @Override public void visitMethodInsn(int opcode,String owner,String called,String descriptor,boolean itf){
                            if(opcode==Opcodes.INVOKESTATIC&&owner.equals("net/minecraft/world/item/CreativeModeTabs")&&called.equals("tabs")&&descriptor.equals("()Ljava/util/List;")){
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,HOOK,"visibleTabs",descriptor,false);hooks[0]++;return;
                            }
                            if(opcode==Opcodes.INVOKEVIRTUAL&&owner.equals(TAB)&&called.equals("column")&&descriptor.equals("()I")){
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,HOOK,"visualColumn","(Ljava/lang/Object;)I",false);hooks[1]++;return;
                            }
                            if(opcode==Opcodes.INVOKEVIRTUAL&&owner.equals(TAB)&&called.equals("row")&&descriptor.equals("()L"+ROW+";")){
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,HOOK,"visualRow","(Ljava/lang/Object;)Ljava/lang/Object;",false);super.visitTypeInsn(Opcodes.CHECKCAST,ROW);hooks[1]++;return;
                            }
                            super.visitMethodInsn(opcode,owner,called,descriptor,itf);
                        }
                    };
                }
            },0);
            if(hooks[0]!=5||hooks[1]!=4||hooks[2]!=1||hooks[3]!=1||hooks[4]!=1){
                System.err.println("[H.O.W.L.] Creative page seams changed; refusing patch: "+java.util.Arrays.toString(hooks));return null;
            }
            System.out.println("[H.O.W.L.] Native creative tab pages enabled.");return writer.toByteArray();
        }catch(RuntimeException e){System.err.println("[H.O.W.L.] Creative pages refused: "+e);return null;}
    }
}
