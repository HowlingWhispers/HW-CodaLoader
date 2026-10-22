package dev.howlingwhispers.codaloader.bootstrap;
import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/** Test-only driver: use Mojang's headless GameTest world, with our smoke scenario instead of its test runner. */
public final class LivingWorldNativeAgent implements ClassFileTransformer {
    public static void premain(String args,Instrumentation i){CodaSettlerAvatarTransformer.install(i);i.addTransformer(new LivingWorldNativeAgent());}
    public byte[] transform(ClassLoader loader,String name,Class<?> redefined,ProtectionDomain domain,byte[] bytes){
        if(!name.equals("net/minecraft/gametest/framework/GameTestServer"))return null;
        var reader=new ClassReader(bytes);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            public MethodVisitor visitMethod(int access,String method,String desc,String sig,String[] exceptions){
                if(method.equals("tickServer")&&desc.equals("(Ljava/util/function/BooleanSupplier;)V")){
                    MethodVisitor mv=super.visitMethod(access,method,desc,sig,exceptions);mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitVarInsn(Opcodes.ALOAD,1);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/server/MinecraftServer","tickServer",desc,false);
                    mv.visitVarInsn(Opcodes.ALOAD,0);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/howlingwhispers/codaloader/bootstrap/LivingWorldNativeSmoke","tick","(Ljava/lang/Object;)V",false);
                    mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
                }
                if(method.equals("onServerExit")&&desc.equals("()V")){
                    MethodVisitor mv=super.visitMethod(access,method,desc,sig,exceptions);mv.visitCode();
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/howlingwhispers/codaloader/bootstrap/LivingWorldNativeSmoke","finish","()V",false);
                    mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
                }
                return super.visitMethod(access,method,desc,sig,exceptions);
            }
        },0);return writer.toByteArray();
    }
}
