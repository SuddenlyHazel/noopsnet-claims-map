// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.claimsmap;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

/** Checks actual pinned bytecode without initializing or launching a Minecraft client. */
class XaeroCompatibilityTest {
    private Map<String, ClassNode> dependencyTypes() throws IOException {
        Map<String, ClassNode> result = new HashMap<>();
        try (var jars = Files.list(Path.of(System.getProperty("claimsmap.dependencies")))) {
            for (Path jar :
                    jars.filter(
                                    p ->
                                            p.getFileName().toString().startsWith("xaero")
                                                    && p.toString().endsWith(".jar"))
                            .toList()) {
                try (var zip = new ZipFile(jar.toFile())) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        if (!entry.getName().startsWith("xaero/")
                                || !entry.getName().endsWith(".class")) continue;
                        ClassNode node = new ClassNode();
                        new ClassReader(zip.getInputStream(entry))
                                .accept(node, ClassReader.SKIP_CODE);
                        result.put(node.name, node);
                    }
                }
            }
        }
        return result;
    }

    private boolean method(
            Map<String, ClassNode> types, String owner, String name, String descriptor) {
        ClassNode type = types.get(owner);
        if (type == null) return false;
        if (type.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)))
            return true;
        if (type.superName != null && method(types, type.superName, name, descriptor)) return true;
        return type.interfaces.stream().anyMatch(i -> method(types, i, name, descriptor));
    }

    private boolean field(
            Map<String, ClassNode> types, String owner, String name, String descriptor) {
        ClassNode type = types.get(owner);
        return type != null
                && (type.fields.stream()
                                .anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor))
                        || type.superName != null
                                && field(types, type.superName, name, descriptor));
    }

    @Test
    void everyXaeroCallAndFieldMatchesThePinnedJars() throws IOException {
        var types = dependencyTypes();
        assertTrue(types.containsKey("xaero/map/element/render/ElementRenderer"));
        assertTrue(types.containsKey("xaero/hud/minimap/element/render/MinimapElementRenderer"));
        int checked = 0;
        try (var classes = Files.walk(Path.of(System.getProperty("claimsmap.clientClasses")))) {
            for (Path path : classes.filter(p -> p.toString().endsWith(".class")).toList()) {
                ClassNode node = new ClassNode();
                new ClassReader(Files.readAllBytes(path)).accept(node, 0);
                for (var m : node.methods) {
                    for (AbstractInsnNode instruction : m.instructions) {
                        if (instruction instanceof MethodInsnNode call
                                && call.owner.startsWith("xaero/")) {
                            assertTrue(
                                    method(types, call.owner, call.name, call.desc),
                                    call.owner + "." + call.name + call.desc);
                            checked++;
                        } else if (instruction instanceof FieldInsnNode access
                                && access.owner.startsWith("xaero/")) {
                            assertTrue(
                                    field(types, access.owner, access.name, access.desc),
                                    access.owner + "." + access.name);
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue(
                checked >= 15, "Renderer compatibility assertions must cover real adapter calls");
    }

    @Test
    void mapActionCoordinatesHavePinnedTypes() throws IOException {
        var types = dependencyTypes();
        for (String name : new String[] {"rightClickX", "rightClickZ"})
            assertTrue(field(types, "xaero/map/gui/GuiMap", name, "I"), name);
        assertTrue(
                field(
                        types,
                        "xaero/map/gui/GuiMap",
                        "rightClickDim",
                        "Lnet/minecraft/resources/ResourceKey;"));
    }

    @Test
    void nativeMenuMixinTargetsPinnedSelectionAndReturnType() throws IOException {
        var types = dependencyTypes();
        assertTrue(
                field(
                        types,
                        "xaero/map/gui/GuiMap",
                        "mapTileSelection",
                        "Lxaero/map/gui/MapTileSelection;"));
        assertTrue(
                method(
                        types,
                        "xaero/map/gui/GuiMap",
                        "getRightClickOptions",
                        "()Ljava/util/ArrayList;"));
        for (String name : new String[] {"getLeft", "getRight", "getTop", "getBottom"})
            assertTrue(method(types, "xaero/map/gui/MapTileSelection", name, "()I"));
    }

    @Test
    void minimapRegistrationAndRotationFieldsHaveExpectedTypes() throws IOException {
        var types = dependencyTypes();
        assertTrue(
                field(
                        types,
                        "xaero/common/minimap/render/MinimapFBORenderer",
                        "minimapElementMapRendererHandler",
                        "Lxaero/hud/minimap/element/render/map/MinimapElementMapRendererHandler;"));
        for (String name : new String[] {"ps", "pc", "zoom"})
            assertTrue(
                    field(
                            types,
                            "xaero/hud/minimap/element/render/map/MinimapElementMapRendererHandler",
                            name,
                            "D"),
                    name);
    }
}
