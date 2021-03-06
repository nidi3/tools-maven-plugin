/*
 * Copyright © 2014 Stefan Niederhauser (nidin@gmx.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guru.nidi.tools.dependency;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import static guru.nidi.tools.dependency.IoUtil.fileEnding;

public class DotProcessor {
    private final File inputDir;
    private final File outputDir;
    private final boolean simple;

    public DotProcessor(File inputDir, File outputDir, boolean simple) {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        this.simple = simple;
    }

    public File[] findDotFiles() {
        return inputDir.listFiles(file -> file.getName().endsWith(".dot"));
    }

    public void executeDots(File[] files) {
        final ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        final List<String> messages = new ArrayList<>();
        for (final File f : files) {
            es.submit(() -> {
                try {
                    executeDot(f);
                } catch (Exception e) {
                    messages.add(e.getMessage());
                }
            });
        }
        es.shutdown();
        try {
            es.awaitTermination(10, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (!messages.isEmpty()) {
            throw new RuntimeException("Problem(s) generating images: " + messages);
        }
    }

    private void executeDot(File f) throws IOException, InterruptedException {
        final File png = fileEnding(f, outputDir, ".png");
        final File map = fileEnding(f, inputDir, ".map");
        if (!png.exists() || (!simple && !map.exists())) {
            final List<String> args = new ArrayList<>(Arrays.asList("dot", f.getName(), "-Tpng", "-o" + png.getAbsolutePath()));
            if (!simple) {
                args.addAll(Arrays.asList("-Tcmapx", "-o" + map.getAbsolutePath()));
            }
            final Process dot = new ProcessBuilder(args).directory(f.getParentFile()).redirectErrorStream(true).inheritIO().start();
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                final InputStream in = dot.getInputStream();
//                byte[] buf = new byte[10000];
//                while (true) {
//                    try {
//                        int read = in.read(buf);
//                        if (read > 0) {
//                            System.out.print(new String(buf, 0, read));
//                        }
//                        Thread.sleep(50);
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }).start();
            dot.waitFor(1, TimeUnit.MINUTES);
            if (!png.exists()) {
                throw new IOException("Image was not created. Make sure Graphviz is installed correctly.");
            }
        }
    }
}
