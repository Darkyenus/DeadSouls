package com.darkyen.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class GlobTest {

	@Test
	public void globTest() {
		assertTrue(Util.compileSimpleGlob("*").matcher("").matches());
		assertTrue(Util.compileSimpleGlob("*").matcher("world").matches());
		assertTrue(Util.compileSimpleGlob("*").matcher("world.*&^~$#@").matches());

		assertTrue(Util.compileSimpleGlob("world").matcher("world").matches());
		assertFalse(Util.compileSimpleGlob("world").matcher("World").matches());
		assertFalse(Util.compileSimpleGlob("world").matcher("world1").matches());
		assertFalse(Util.compileSimpleGlob("world").matcher(".worl").matches());

		assertTrue(Util.compileSimpleGlob("world*").matcher("world").matches());
		assertTrue(Util.compileSimpleGlob("world*").matcher("worlds").matches());
		assertTrue(Util.compileSimpleGlob("world*").matcher("world{*&^~$#@()").matches());

		assertTrue(Util.compileSimpleGlob("wo*rld*").matcher("world{*&^~$#@()").matches());
		assertTrue(Util.compileSimpleGlob("wo*rld*").matcher("wo123456789rld{*&^~$#@()").matches());
		assertFalse(Util.compileSimpleGlob("wo*rld*").matcher("w123456789orld{*&^~$#@()").matches());

		assertFalse(Util.compileSimpleGlob("*wo*rld*").matcher("w123456789orld{*&^~$#@()").matches());
		assertTrue(Util.compileSimpleGlob("*wo*rld*").matcher("wo123456789orld{*&^~$#@()").matches());
		assertTrue(Util.compileSimpleGlob("*wo*rld*").matcher(",.,-.,)§)!:_?(/`!wo123456789orld{*&^~$#@()").matches());
	}

}
