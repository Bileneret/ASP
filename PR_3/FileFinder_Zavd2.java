import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

public class FileFinder_Zavd2 {

    private static final Scanner scanner = new Scanner(System.in);

    private static String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static long readLong(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Long.parseLong(scanner.nextLine().trim());
            } catch (Exception e) {
                System.out.println("Невірне число. Спробуйте ще.");
            }
        }
    }

    // -------------------- Збір файлів --------------------

    private static List<Path> collectFiles(Path start, boolean recursive) throws IOException {
        List<Path> files = new ArrayList<>();

        if (!recursive) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(start)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) {
                        files.add(entry);
                    }
                }
            }
            return files;
        }

        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (Files.isRegularFile(file)) files.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // Перевіряємо доступ до директорії
                if (!Files.isReadable(dir)) {
                    System.out.println("Пропущено директорію через відсутність доступу: " + dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.out.println("Не вдалося відкрити файл: " + file);
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    // -------------------- Work Stealing --------------------

    static class FileCountTask extends RecursiveTask<List<Path>> {
        private static final int THRESHOLD = 100;
        final List<Path> list;
        final int s, e;
        final long minBytes;

        FileCountTask(List<Path> list, int s, int e, long minBytes) {
            this.list = list;
            this.s = s;
            this.e = e;
            this.minBytes = minBytes;
        }

        @Override
        protected List<Path> compute() {
            int len = e - s;
            if (len <= THRESHOLD) {
                List<Path> res = new ArrayList<>();
                for (int i = s; i < e; i++) {
                    Path p = list.get(i);
                    try {
                        if (Files.size(p) >= minBytes) res.add(p);
                    } catch (IOException ignored) {
                    }
                }
                return res;
            } else {
                int mid = s + len / 2;
                FileCountTask left = new FileCountTask(list, s, mid, minBytes);
                FileCountTask right = new FileCountTask(list, mid, e, minBytes);
                left.fork();
                List<Path> rr = right.compute();
                List<Path> lr = left.join();
                lr.addAll(rr);
                return lr;
            }
        }
    }

    // -------------------- Work Dealing --------------------

    private static List<Path> countWithFixedPool(List<Path> files, int workers, long minBytes) throws InterruptedException {
        int n = files.size();
        List<List<Path>> parts = new ArrayList<>();
        for (int i = 0; i < workers; i++) parts.add(new ArrayList<>());
        for (int i = 0; i < n; i++) parts.get(i % workers).add(files.get(i));

        ExecutorService ex = Executors.newFixedThreadPool(workers);
        List<Future<List<Path>>> futures = new ArrayList<>();

        for (int w = 0; w < workers; w++) {
            final List<Path> part = parts.get(w);
            futures.add(ex.submit(() -> {
                List<Path> out = new ArrayList<>();
                for (Path p : part) {
                    try {
                        if (Files.size(p) >= minBytes) out.add(p);
                    } catch (IOException ignored) {
                    }
                }
                return out;
            }));
        }

        List<Path> result = new ArrayList<>();
        for (Future<List<Path>> f : futures) {
            try {
                result.addAll(f.get());
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        ex.shutdown();
        ex.awaitTermination(1, TimeUnit.MINUTES);
        return result;
    }

    // -------------------- Запис результатів --------------------

    private static void writeResultsToFile(
            double collectTime,
            double wtProcess, double wtTotal,
            double wdProcess, double wdTotal,
            long minKB, int threads,
            List<Path> matched
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("============================\n");
        sb.append("       FILE COLLECTING\n");
        sb.append("============================\n");
        sb.append(String.format("Час збору файлів: %.3f ms%n", collectTime));

        sb.append("============================\n");
        sb.append("        Work stealing\n");
        sb.append("============================\n");
        sb.append(String.format("Час обробки (ForkJoin): %.3f ms%n", wtProcess));
        sb.append(String.format("Загальний час: %.3f ms%n", wtTotal));

        sb.append("============================\n");
        sb.append("        Work dealing\n");
        sb.append("============================\n");
        sb.append("Кількість потоків: ").append(threads).append("\n");
        sb.append(String.format("Час обробки (FixedPool): %.3f ms%n", wdProcess));
        sb.append(String.format("Загальний час: %.3f ms%n", wdTotal));

        sb.append("============================\n");
        if (wtTotal < wdTotal) {
            sb.append(String.format("Work stealing - %.3f ms <- швидше%n", wtTotal));
            sb.append(String.format("Work dealing - %.3f ms %n", wdTotal));
        } else {
            sb.append(String.format("Work stealing - %.3f ms %n", wtTotal));
            sb.append(String.format("Work dealing - %.3f ms <- швидше%n", wdTotal));
        }
        sb.append("============================\n");

        sb.append("Знайдені файли > ").append(minKB).append(" КБ:\n\n");

        for (Path p : matched) {
            try {
                long bytes = Files.size(p);
                long kbsz = (bytes + 1023) / 1024;
                sb.append(p.getFileName().toString()).append("  |  ").append(kbsz).append(" КБ\n");
            } catch (IOException ignored) {
            }
        }

        sb.append("============================\n");

        try {
            Files.writeString(Paths.get("FinderResult.txt"), sb.toString());
        } catch (IOException e) {
            System.out.println("Помилка запису у файл FinderResult.txt");
        }
    }

    // -------------------- MAIN --------------------

    public static void main(String[] args) throws Exception {

        System.out.println("=== File Finder (Варіант №4) ===");

        String dir = readLine("Введіть шлях до директорії: ");
        Path start = Paths.get(dir);
        if (!Files.exists(start) || !Files.isDirectory(start)) {
            System.out.println("Вказано невірну директорію.");
            return;
        }

        String rec = readLine("Переглядати підпапки? Y/N: ");
        boolean recursive = rec.equalsIgnoreCase("Y");

        long kb = readLong("Введіть мінімальний розмір файлу у КБ: ");
        if (kb < 0) kb = 0;
        long minBytes = kb * 1024;

        readLine("\nГотові? Натисніть Enter...");

        // ==================== Збір файлів ====================
        long collectStart = System.nanoTime();
        List<Path> allFiles = collectFiles(start, recursive);
        long collectEnd = System.nanoTime();
        double collectTime = (collectEnd - collectStart) / 1_000_000.0;
        System.out.printf("Збір файлів завершено. Знайдено всього %d файлів. Час: %.3f ms%n", allFiles.size(), collectTime);

        // ==================== Work Stealing ====================
        long wtProcessStart = System.nanoTime();
        ForkJoinPool fj = new ForkJoinPool();
        FileCountTask root = new FileCountTask(allFiles, 0, allFiles.size(), minBytes);
        List<Path> matchedSteal = fj.invoke(root);
        long wtProcessEnd = System.nanoTime();
        fj.shutdown();

        double wtProcess = (wtProcessEnd - wtProcessStart) / 1_000_000.0;
        double wtTotal = wtProcess; // тепер total = process, бо збір файлів винесено окремо

        System.out.printf("\nWork Stealing завершено. Час обробки: %.3f ms%n", wtProcess);
        readLine("Натисніть Enter для Work Dealing...");

        // ==================== Work Dealing ====================
        int threads;
        try {
            threads = Integer.parseInt(readLine("Кількість потоків (натисніть Enter для автопідбору): "));
            if (threads <= 0) threads = Runtime.getRuntime().availableProcessors();
        } catch (Exception e) {
            threads = Runtime.getRuntime().availableProcessors();
        }

        long wdProcessStart = System.nanoTime();
        List<Path> matchedDeal = countWithFixedPool(allFiles, threads, minBytes);
        long wdProcessEnd = System.nanoTime();

        double wdProcess = (wdProcessEnd - wdProcessStart) / 1_000_000.0;
        double wdTotal = wdProcess;

        System.out.printf("\nWork Dealing завершено. Час обробки: %.3f ms%n", wdProcess);
        readLine("Натисніть Enter для порівняння...");

        //===================== Вивід ======================
        String fasterSteal = wtTotal < wdTotal ? "\033[1;32mWork Stealing\033[0m" : "Work Stealing";
        String fasterDeal = wdTotal < wtTotal ? "\033[1;32mWork Dealing\033[0m" : "Work Dealing";

        System.out.println("\n====================");
        System.out.println("     Порівняння");
        System.out.println("====================");
        System.out.printf("%s = %.3f ms%n", fasterSteal, wtTotal);
        System.out.printf("%s = %.3f ms%n", fasterDeal, wdTotal);

        //===================== Запис у файл ======================
        writeResultsToFile(
                collectTime,
                wtProcess, wtTotal,
                wdProcess, wdTotal,
                kb, threads,
                wtTotal < wdTotal ? matchedSteal : matchedDeal
        );

        System.out.println("\nРезультати записано у FinderResult.txt");

        //===================== Вивести результати? ======================
        String show = readLine("Хочете вивести результати тут? Y/N: ");
        if (show.equalsIgnoreCase("Y")) {
            System.out.println();
            String text = Files.readString(Paths.get("FinderResult.txt"));
            System.out.println(text);
        }
    }
}
