package com.github.gradusnikov.eclipse.assistai.mcp.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Creatable
@Singleton
public class MemoryStoreService
{
    private static final String MEMORY_DIR = ".assistai";
    private static final String MEMORY_FILE = "memory.json";
    private static final String THOUGHTS_FILE = "thoughts.log";
    private static final String THOUGHTS_BACKUP = "thoughts.1.log";
    private static final long MAX_THOUGHTS_SIZE = 1024 * 1024; // 1 MB
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" );

    @Inject
    protected ILog logger;

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper().enable( SerializationFeature.INDENT_OUTPUT );
    private final ExecutorService writer = Executors.newSingleThreadExecutor( Thread.ofVirtual().name( "assistai-memory-writer" ).factory() );
    private volatile boolean loaded = false;

    private void ensureLoaded()
    {
        if ( !loaded )
        {
            synchronized ( this )
            {
                if ( !loaded )
                {
                    loadFromDisk();
                    loaded = true;
                }
            }
        }
    }

    public String remember( String key, String value )
    {
        ensureLoaded();
        store.put( key, value );
        writer.execute( this::saveToDisk );
        return value;
    }

    public String recall( String key )
    {
        ensureLoaded();
        return store.get( key );
    }

    public Map<String, String> listMemories()
    {
        ensureLoaded();
        return Collections.unmodifiableMap( new LinkedHashMap<>( store ) );
    }

    public boolean forget( String key )
    {
        ensureLoaded();
        boolean existed = store.remove( key ) != null;
        if ( existed )
        {
            writer.execute( this::saveToDisk );
        }
        return existed;
    }

    public void logThought( String thought )
    {
        LocalDateTime timestamp = LocalDateTime.now();
        writer.execute( () -> writeThought( timestamp, thought ) );
    }

    private Path getMemoryDir()
    {
        return resolveMemoryDir();
    }

    protected Path resolveMemoryDir()
    {
        Path workspaceRoot = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
        return workspaceRoot.resolve( MEMORY_DIR );
    }

    private Path getMemoryFile()
    {
        return getMemoryDir().resolve( MEMORY_FILE );
    }

    private void loadFromDisk()
    {
        Path file = getMemoryFile();
        if ( !Files.exists( file ) )
        {
            return;
        }
        try
        {
            byte[] bytes = Files.readAllBytes( file );
            Map<String, String> data = mapper.readValue( bytes, new TypeReference<Map<String, String>>(){} );
            if ( data != null )
            {
                store.putAll( data );
            }
        }
        catch ( IOException e )
        {
            logger.error( "Failed to load memory store from " + file, e );
        }
    }

    private void saveToDisk()
    {
        Path file = getMemoryFile();
        Path parent = file.getParent();
        if ( parent == null )
        {
            return;
        }
        try
        {
            Files.createDirectories( parent );
            byte[] bytes = mapper.writeValueAsBytes( store );
            Files.write( file, bytes );
        }
        catch ( IOException e )
        {
            logger.error( "Failed to save memory store to " + file, e );
        }
    }

    private void writeThought( LocalDateTime timestamp, String thought )
    {
        Path dir = getMemoryDir();
        Path file = dir.resolve( THOUGHTS_FILE );
        try
        {
            Files.createDirectories( dir );
            if ( Files.exists( file ) && Files.size( file ) > MAX_THOUGHTS_SIZE )
            {
                Path backup = dir.resolve( THOUGHTS_BACKUP );
                Files.deleteIfExists( backup );
                Files.move( file, backup );
            }
            String entry = "[" + TIMESTAMP_FMT.format( timestamp ) + "] " + thought + "\n\n";
            Files.writeString( file, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND );
        }
        catch ( IOException e )
        {
            logger.error( "Failed to log thought to " + file, e );
        }
    }
}
