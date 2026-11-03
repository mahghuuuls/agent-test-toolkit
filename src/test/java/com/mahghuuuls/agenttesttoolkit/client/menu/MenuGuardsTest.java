package com.mahghuuuls.agenttesttoolkit.client.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure guards of the client request channel (rule 18, REQ-216 to REQ-219). */
class MenuGuardsTest {

    private static MenuRequest parse(String line) {
        return MenuRequest.parse("r1", line);
    }

    private static void assertRefused(String line, String reason) {
        MenuRequest request = parse(line);
        assertFalse(request.isAccepted(), line);
        assertEquals(reason, request.getRefusal(), line);
    }

    @Test
    @DisplayName("only create, open, connect and quit are steps, each with its arguments")
    void steps() {
        assertEquals("quit", parse("quit").getStep());
        assertEquals("open", parse("open New_World-2").getStep());
        assertEquals("connect", parse("connect localhost:25570").getStep());
        assertEquals("connect", parse("connect [::1]:25570").getStep());
        assertEquals("create", parse("create test1 type=flat structures=false").getStep());
        assertRefused("run v1", MenuRequest.REASON_VERB_NOT_ALLOWED);
        assertRefused("devtool run v1", MenuRequest.REASON_VERB_NOT_ALLOWED);
        assertRefused("say hi", MenuRequest.REASON_VERB_NOT_ALLOWED);
        assertRefused("quit now", MenuRequest.REASON_BAD_ARGUMENTS);
        assertRefused("open", MenuRequest.REASON_BAD_ARGUMENTS);
        assertRefused("open ../other", MenuRequest.REASON_BAD_ARGUMENTS);
        assertRefused("connect", MenuRequest.REASON_BAD_ARGUMENTS);
        assertRefused("connect a b", MenuRequest.REASON_BAD_ARGUMENTS);
        assertRefused("create", MenuRequest.REASON_BAD_ARGUMENTS);
        assertRefused("create my.world", MenuRequest.REASON_BAD_ARGUMENTS);
        assertRefused("create x mode=survivor", MenuRequest.REASON_BAD_ARGUMENTS);
        assertEquals("unknownMode:survivor", parse("create x mode=survivor").getDetail(), "the detail is kept");
        assertRefused("create abcdefghijklmnopqrstuvwxyz0123456", MenuRequest.REASON_BAD_ARGUMENTS);
        assertEquals("New World", parse("open New World").getArgs().get(0), "a save folder with a space");
        assertRefused("open New  World/..", MenuRequest.REASON_BAD_ARGUMENTS);
    }

    @Test
    @DisplayName("an empty, multi-line, non-ASCII or over-long request is malformed")
    void malformed() {
        assertRefused("", MenuRequest.REASON_MALFORMED);
        assertRefused("quit\nquit", MenuRequest.REASON_MALFORMED);
        assertRefused("open wé", MenuRequest.REASON_MALFORMED);
        StringBuilder big = new StringBuilder("create a seed=");
        for (int i = 0; i < 600; i++) {
            big.append('1');
        }
        assertRefused(big.toString(), MenuRequest.REASON_MALFORMED);
        assertTrue(parse("quit\r\n").isAccepted(), "one trailing line break is fine");
        assertTrue(parse("﻿quit").isAccepted(), "a byte order mark is ignored");
        assertFalse(MenuRequest.parse("r1", null).isAccepted());
    }

    @Test
    @DisplayName("create defaults to creative with cheats on, and refuses any unknown or repeated option")
    void createDefaults() {
        WorldOptions o = WorldOptions.parse(Collections.<String>emptyList());
        assertTrue(o.isValid());
        assertEquals("creative", o.getMode());
        assertTrue(o.isCheats());
        assertTrue(o.isStructures());
        assertEquals("default", o.getType());
        assertNull(o.getFlat());
        assertNull(o.seed(), "a random seed");
        for (String bad : new String[] {"mode=survivor", "type=customized", "type=debug_all_block_states",
                "type=default_1_1", "cheats=yes", "structures=1", "colour=red", "seed", "=x", "flat=3;x;1;"}) {
            assertFalse(WorldOptions.parse(Arrays.asList(bad)).isValid(), bad);
        }
        assertFalse(WorldOptions.parse(Arrays.asList("mode=creative", "mode=survival")).isValid(), "repeated");
        WorldOptions all = WorldOptions.parse(Arrays.asList("seed=42", "type=amplified", "structures=false",
                "cheats=false", "mode=adventure", "bonusChest=true"));
        assertTrue(all.isValid());
        assertEquals(Long.valueOf(42L), all.seed());
        assertEquals("amplified", all.getType());
        assertFalse(all.isStructures());
        assertFalse(all.isCheats());
        assertEquals("adventure", all.getMode());
        assertTrue(all.isBonusChest());
        assertEquals(Long.valueOf("hello".hashCode()), WorldOptions.parse(Arrays.asList("seed=hello")).seed(),
                "text is hashed as the screen does");
        assertNull(WorldOptions.parse(Arrays.asList("seed=0")).seed(), "zero is random, as on the screen");
        assertNull(WorldOptions.parse(Arrays.asList("seed=00")).seed());
    }

    @Test
    @DisplayName("a flat world without structures has no structure part, so no village")
    void flat() {
        WorldOptions plain = WorldOptions.parse(Arrays.asList("type=flat"));
        assertEquals("3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;village", plain.getFlat());
        WorldOptions none = WorldOptions.parse(Arrays.asList("type=flat", "structures=false"));
        assertEquals("3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;", none.getFlat());
        WorldOptions own = WorldOptions.parse(Arrays.asList("type=flat",
                "flat=3;minecraft:bedrock,3*minecraft:stone:1;1;village,mineshaft", "structures=false"));
        assertEquals("3;minecraft:bedrock,3*minecraft:stone:1;1;", own.getFlat());
        WorldOptions lakes = WorldOptions.parse(Arrays.asList("type=flat",
                "flat=3;minecraft:stone;1;village,lake,dungeon(chance=4),stronghold", "structures=false"));
        assertEquals("3;minecraft:stone;1;lake,dungeon(chance=4)", lakes.getFlat(), "only structures go");
        assertFalse(WorldOptions.parse(Arrays.asList("flat=3;minecraft:stone;1;")).isValid(), "flat needs type=flat");
        for (String bad : new String[] {"2;minecraft:stone;1;", "3;;1;", "3;minecraft:stone;300;",
                "3;300*minecraft:stone;1;", "3;0*minecraft:stone;1;", "3;stone;1;", "3;minecraft:stone;1",
                "3;minecraft:stone:16;1;", "3;minecraft:stone;1;vilage", "3;minecraft:stone;1;village(size=1 distance=9)"}) {
            assertFalse(WorldOptions.parse(Arrays.asList("type=flat", "flat=" + bad)).isValid(), bad);
        }
        Set<String> known = new HashSet<String>(Arrays.asList("minecraft:bedrock", "minecraft:dirt", "minecraft:grass"));
        assertNull(plain.checkRegistry(known::contains, biome -> biome == 1));
        WorldOptions unknown = WorldOptions.parse(Arrays.asList("type=flat", "flat=3;minecraft:bedrock,modx:goo;1;"));
        assertEquals("unknownBlock:modx:goo", unknown.checkRegistry(known::contains, biome -> true));
        WorldOptions biome = WorldOptions.parse(Arrays.asList("type=flat", "flat=3;minecraft:bedrock;200;"));
        assertEquals("unknownBiome:200", biome.checkRegistry(known::contains, b -> b < 170));
    }

    @Test
    @DisplayName("a new world never takes an existing folder, and a device name is wrapped as the screen does")
    void folders() {
        assertEquals("test1", SaveFolders.folderFor("test1"));
        assertEquals("_CON_", SaveFolders.folderFor("CON"));
        assertEquals("_com1_", SaveFolders.folderFor("com1"));
        assertEquals("_com_", SaveFolders.folderFor("com"), "plain COM, as the screen's list has");
        Set<String> existing = new HashSet<String>(Arrays.asList("test1"));
        assertFalse(SaveFolders.isFree("test1", existing::contains));
        assertTrue(SaveFolders.isFree("test2", existing::contains));
    }

    @Test
    @DisplayName("connect goes only to this computer or the local network")
    void localAddresses() throws Exception {
        for (String local : new String[] {"127.0.0.1", "127.5.6.7", "10.0.0.2", "172.16.0.1", "172.31.255.1",
                "192.168.1.20", "169.254.3.4", "::1", "fe80::1", "fd12:3456::1", "fc00::5"}) {
            assertTrue(LocalAddresses.isLocal(InetAddress.getByName(local)), local);
        }
        for (String remote : new String[] {"0.0.0.0", "::", "8.8.8.8", "172.32.0.1", "100.64.0.1", "100.127.255.1",
                "2001:4860::8888", "fec0::1", "224.0.0.1", "fd7a:115c:a1e0::5"}) {
            assertFalse(LocalAddresses.isLocal(InetAddress.getByName(remote)), remote);
        }
        assertFalse(LocalAddresses.allLocal(Collections.<InetAddress>emptyList()), "nothing resolved");
        assertFalse(LocalAddresses.allLocal(Arrays.asList(InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("8.8.8.8"))), "one public address is enough to refuse");
    }

    @Test
    @DisplayName("a connect address splits into host and port, with the default port and bracketed IPv6")
    void hostPort() {
        assertArrayEquals(new String[] {"localhost", "25570"}, MenuChannel.splitHostPort("localhost:25570"));
        assertArrayEquals(new String[] {"localhost", "25565"}, MenuChannel.splitHostPort("localhost"));
        assertArrayEquals(new String[] {"::1", "25570"}, MenuChannel.splitHostPort("[::1]:25570"));
        assertArrayEquals(new String[] {"::1", "25565"}, MenuChannel.splitHostPort("[::1]"));
        assertNull(MenuChannel.splitHostPort("a:b:c"));
        assertNull(MenuChannel.splitHostPort("host:0"));
        assertNull(MenuChannel.splitHostPort("host:70000"));
        assertNull(MenuChannel.splitHostPort("host:"));
        assertNull(MenuChannel.splitHostPort("[::1"));
    }
}
