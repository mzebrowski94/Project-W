/**
 * Reading what the game starts with (ADR 0021): the client's one door to a config file, and the loaders
 * for the configs the client does not own.
 *
 * <p>Ports: {@link pl.mzebrows.pwt.client.config.ConfigLoader} (finds a file, binds it, says what was
 * wrong - every config record loads through it), {@link pl.mzebrows.pwt.client.config.GameProperties} (a
 * file off the classpath, as the interface's words are read) and {@link
 * pl.mzebrows.pwt.client.config.SimConfigLoader} (the sim's config, which a session's worlds are parsed
 * with). Entry: {@code NetConfigLoader}.
 *
 * <p>Not here: a config record the client owns - it lives with its layer ({@code GraphicConfig} in
 * {@code draw}, {@code ControlsConfig} in {@code input}) - or the command line ({@code LaunchOptions}, the
 * root's). No libGDX.
 *
 * <p>Answers to ADR 0018.
 */
@NullMarked
package pl.mzebrows.pwt.client.config;

import org.jspecify.annotations.NullMarked;
