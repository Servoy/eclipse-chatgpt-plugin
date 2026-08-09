package com.github.gradusnikov.eclipse.plugin.assistai.mcp.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.gradusnikov.eclipse.assistai.mcp.servers.MemoryMcpServer;
import com.github.gradusnikov.eclipse.assistai.mcp.services.MemoryStoreService;

public class MemoryStoreServicePDETest
{
    @TempDir
    Path tempDir;

    private MemoryMcpServer server;
    private MemoryStoreService store;

    @BeforeEach
    void setUp() throws Exception
    {
        store = new MemoryStoreService()
        {
            @Override
            protected Path resolveMemoryDir()
            {
                return tempDir.resolve( ".assistai" );
            }
        };
        var loggerField = MemoryStoreService.class.getDeclaredField( "logger" );
        loggerField.setAccessible( true );
        loggerField.set( store, org.eclipse.core.runtime.Platform.getLog( MemoryStoreServicePDETest.class ) );

        server = new MemoryMcpServer();
        var storeField = MemoryMcpServer.class.getDeclaredField( "memoryStore" );
        storeField.setAccessible( true );
        storeField.set( server, store );
    }

    @AfterEach
    void tearDown() throws Exception
    {
        var writerField = MemoryStoreService.class.getDeclaredField( "writer" );
        writerField.setAccessible( true );
        ExecutorService writer = (ExecutorService) writerField.get( store );
        writer.shutdown();
        writer.awaitTermination( 5, java.util.concurrent.TimeUnit.SECONDS );
    }

    @Test
    void rememberAndRecall()
    {
        server.remember( "test-key", "test-value" );
        assertEquals( "test-value", server.recall( "test-key" ) );
    }

    @Test
    void recallUnknownKey()
    {
        String result = server.recall( "nonexistent" );
        assertTrue( result.contains( "No memory found" ) );
    }

    @Test
    void listMemoriesEmpty()
    {
        String result = server.listMemories();
        assertTrue( result.contains( "No memories stored" ) );
    }

    @Test
    void listMemoriesShowsEntries()
    {
        server.remember( "a", "1" );
        server.remember( "b", "2" );
        String result = server.listMemories();
        assertTrue( result.contains( "a = 1" ) );
        assertTrue( result.contains( "b = 2" ) );
    }

    @Test
    void listMemoriesTruncatesLongValues()
    {
        String longValue = "x".repeat( 100 );
        server.remember( "key", longValue );
        String result = server.listMemories();
        assertTrue( result.contains( "..." ) );
        assertTrue( result.length() < longValue.length() + 20 );
    }

    @Test
    void forgetRemovesKey()
    {
        server.remember( "key", "value" );
        String result = server.forget( "key" );
        assertTrue( result.contains( "Forgotten" ) );
        assertTrue( server.recall( "key" ).contains( "No memory found" ) );
    }

    @Test
    void forgetNonexistent()
    {
        String result = server.forget( "nope" );
        assertTrue( result.contains( "Key not found" ) );
    }

    @Test
    void thinkReturnsThought()
    {
        String result = server.think( "a useful thought" );
        assertEquals( "a useful thought", result );
    }
}
