/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml;

import com.mohistmc.util.i18n.i18n;
import cpw.mods.modlauncher.log.TransformingThrowablePatternConverter;
import joptsimple.internal.Strings;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraftforge.fml.common.ICrashCallable;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;

public class CrashReportExtender
{
    private static List<ICrashCallable> crashCallables = Collections.synchronizedList(new ArrayList<>());

    public static void enhanceCrashReport(final CrashReport crashReport, final CrashReportCategory category)
    {
        for (final ICrashCallable call: crashCallables)
        {
            category.setDetail(call.getLabel(), call);
        }
    }

    public static void registerCrashCallable(ICrashCallable callable)
    {
        crashCallables.add(callable);
    }

    public static void registerCrashCallable(String headerName, Callable<String> reportGenerator) {
        registerCrashCallable(new ICrashCallable() {
            @Override
            public String getLabel() {
                return headerName;
            }
            @Override
            public String call() throws Exception {
                return reportGenerator.call();
            }
        });
    }
    public static void addCrashReportHeader(StringBuilder stringbuilder, CrashReport crashReport)
    {
    }
    public static String generateEnhancedStackTrace(final Throwable throwable) {
        return generateEnhancedStackTrace(throwable, true);
    }

    public static String generateEnhancedStackTrace(final StackTraceElement[] stacktrace) {
        final Throwable t = new Throwable();
        t.setStackTrace(stacktrace);
        return generateEnhancedStackTrace(t, false);
    }

    public static String generateEnhancedStackTrace(final Throwable throwable, boolean header) {
        final String s = TransformingThrowablePatternConverter.generateEnhancedStackTrace(throwable);
        return header ? s : s.substring(s.indexOf(Strings.LINE_SEPARATOR));
    }


    public static File dumpModLoadingCrashReport(final Logger logger, final LoadingFailedException error, final File topLevelDir) {
        final CrashReport crashReport = CrashReport.forThrowable(new Exception("Mod Loading has failed"), "Mod loading error has occurred");
        error.getErrors().forEach(mle -> {
            final Optional<IModInfo> modInfo = Optional.ofNullable(mle.getModInfo());
            final CrashReportCategory category = crashReport.addCategory(modInfo.map(iModInfo -> "MOD "+iModInfo.getModId()).orElse("NO MOD INFO AVAILABLE"));
            Throwable cause = mle.getCause();
            int depth = 0;
            while (cause != null && cause.getCause() != null && cause.getCause()!=cause) {
                category.setDetail("Caused by "+(depth++), cause + generateEnhancedStackTrace(cause.getStackTrace()).replaceAll(Strings.LINE_SEPARATOR+"\t", "\n\t\t"));
                cause = cause.getCause();
            }
            if (cause != null)
                category.applyStackTrace(cause);
            category.setDetail("Mod File", () -> modInfo.map(IModInfo::getOwningFile).map(t->((ModFileInfo)t).getFile().getFileName()).orElse("NO FILE INFO"));
            category.setDetail("Failure message", () -> mle.getCleanMessage().replace("\n", "\n\t\t"));
            category.setDetail("Mod Version", () -> modInfo.map(IModInfo::getVersion).map(Object::toString).orElse("NO MOD INFO AVAILABLE"));
            category.setDetail("Mod Issue URL", () -> modInfo.map(IModInfo::getOwningFile).map(ModFileInfo.class::cast).flatMap(mfi->mfi.<String>getConfigElement("issueTrackerURL")).orElse("NOT PROVIDED"));
            category.setDetail("Exception message", Objects.toString(cause, "MISSING EXCEPTION MESSAGE"));
        });
        final File file1 = new File(topLevelDir, "crash-reports");
        final File file2 = new File(file1, "crash-" + (new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss")).format(new Date()) + "-fml.txt");
        if (crashReport.saveToFile(file2)) {
            logger.fatal(i18n.get("crashreportextender.1", file2));
        } else {
            logger.fatal(i18n.get("crashreportextender.2"));
        }
        System.out.print(crashReport.getFriendlyReport());
        return file2;
    }
}
