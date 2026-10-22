package dev.howlingwhispers.codaloader.bootstrap;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/** Keep Mojang's player-avatar rendering, physics, skin resolution and equipment sync. */
public final class CodaSettlerAvatarTransformer implements ClassFileTransformer {
    static void install(Instrumentation instrumentation) {
        instrumentation.addTransformer(new CodaSettlerAvatarTransformer(), false);
    }
    @Override public byte[] transform(ClassLoader loader,String name,Class<?> redefining,
            ProtectionDomain domain,byte[] original) {
        if (!name.equals("net/minecraft/world/entity/decoration/Mannequin")) return null;
        var reader=new ClassReader(original);var writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        int[] mapped={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions){
                var mv=super.visitMethod(access,method,desc,signature,exceptions);
                if(method.equals("tick")&&desc.equals("()V"))throw new IllegalStateException("Mannequin tick mapping changed");
                String callback=method.equals("addAdditionalSaveData")?"write":method.equals("readAdditionalSaveData")?"read":null;
                String expected="(Lnet/minecraft/world/level/storage/"+(method.equals("addAdditionalSaveData")?"ValueOutput":"ValueInput")+";)V";
                if(callback==null||!desc.equals(expected))return mv;
                mapped[0]++;
                return new MethodVisitor(Opcodes.ASM9,mv){
                    @Override public void visitInsn(int opcode){
                        if(opcode==Opcodes.RETURN){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/howlingwhispers/codaloader/bootstrap/CodaSettlerAvatarLifecycle",callback,
                                    "(Ljava/lang/Object;Ljava/lang/Object;)V",false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
            @Override public void visitEnd(){
                MethodVisitor tick=super.visitMethod(Opcodes.ACC_PUBLIC,"tick","()V",null,null);tick.visitCode();
                tick.visitVarInsn(Opcodes.ALOAD,0);tick.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "dev/howlingwhispers/codaloader/bootstrap/CodaSettlerAvatarLifecycle","beforeTick","(Ljava/lang/Object;)V",false);
                tick.visitVarInsn(Opcodes.ALOAD,0);tick.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/world/entity/Avatar","tick","()V",false);
                tick.visitInsn(Opcodes.RETURN);tick.visitMaxs(0,0);tick.visitEnd();
                MethodVisitor drop=super.visitMethod(Opcodes.ACC_PROTECTED,"dropEquipment","(Lnet/minecraft/server/level/ServerLevel;)V",null,null);drop.visitCode();
                drop.visitVarInsn(Opcodes.ALOAD,0);drop.visitVarInsn(Opcodes.ALOAD,1);drop.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        "net/minecraft/world/entity/Avatar","dropEquipment","(Lnet/minecraft/server/level/ServerLevel;)V",false);
                drop.visitVarInsn(Opcodes.ALOAD,0);drop.visitVarInsn(Opcodes.ALOAD,1);drop.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "dev/howlingwhispers/codaloader/bootstrap/CodaSettlerAvatarLifecycle","drop","(Ljava/lang/Object;Ljava/lang/Object;)V",false);
                drop.visitInsn(Opcodes.RETURN);drop.visitMaxs(0,0);drop.visitEnd();super.visitEnd();
            }
        },0);
        if(mapped[0]!=2)throw new IllegalStateException("Snapshot 3 mannequin persistence mapping changed");
        System.out.println("[H.O.W.L.] Frontier player-avatar movement and inventory persistence installed.");
        return writer.toByteArray();
    }
}
