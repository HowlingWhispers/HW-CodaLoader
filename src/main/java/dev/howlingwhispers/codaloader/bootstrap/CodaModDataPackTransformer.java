package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/** Exact Snapshot 3 repository discovery seam; does not rewrite vanilla data or advancement progress. */
public final class CodaModDataPackTransformer implements ClassFileTransformer {
    static void install(Instrumentation i){i.addTransformer(new CodaModDataPackTransformer(),false);}
    @Override public byte[] transform(ClassLoader loader,String name,Class<?> redefining,ProtectionDomain domain,byte[] bytes){
        if(!"net/minecraft/server/packs/repository/BuiltInPackSource".equals(name))return null;
        try{
            var reader=new ClassReader(bytes);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] count={0,0,0};
            reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
                @Override public MethodVisitor visitMethod(int access,String n,String d,String signature,String[] exceptions){
                    var base=super.visitMethod(access,n,d,signature,exceptions);
                    if(!n.equals("loadPacks")||!d.equals("(Ljava/util/function/Consumer;)V")||(access&Opcodes.ACC_ABSTRACT)!=0)return base;
                    count[0]++;
                    return new MethodVisitor(Opcodes.ASM9,base){
                        @Override public void visitMethodInsn(int op,String owner,String m,String desc,boolean itf){
                            if(owner.endsWith("/CodaModDataPacks"))count[2]++;
                            super.visitMethodInsn(op,owner,m,desc,itf);
                        }
                        @Override public void visitInsn(int op){
                            if(op==Opcodes.RETURN){
                                super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/howlingwhispers/codaloader/bootstrap/CodaModDataPacks","discover","(Ljava/lang/Object;Ljava/util/function/Consumer;)V",false);count[1]++;
                            }
                            super.visitInsn(op);
                        }
                    };
                }
            },0);
            if(count[0]!=1||count[1]!=1||count[2]!=0)throw new IllegalStateException("Unexpected mod data-pack discovery seam");
            return writer.toByteArray();
        }catch(Exception e){throw new IllegalStateException("Snapshot mod data-pack seam changed",e);}
    }
}
