import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class PracticalWork4 {

    // POJO для результатів обробки файлу
    static class FileResult {
        final String filename;
        final String original;
        final List<Character> processed; // символи, що лишилися після видалення букв
        final long readMs;
        final long totalMs;

        FileResult(String filename, String original, List<Character> processed, long readMs, long totalMs) {
            this.filename = filename;
            this.original = original;
            this.processed = processed;
            this.readMs = readMs;
            this.totalMs = totalMs;
        }
    }

    // Для Частини 2 — результати генерації та обчислення
    static class SequenceResult {
        final double[] arr;
        final long genMs;

        SequenceResult(double[] arr, long genMs) {
            this.arr = arr;
            this.genMs = genMs;
        }
    }

    static class ComputationResult {
        final double sum;
        final long computeMs;

        ComputationResult(double sum, long computeMs) {
            this.sum = sum;
            this.computeMs = computeMs;
        }
    }

    public static void main(String[] args) {
        System.out.println("Практична робота №4 — CompletableFuture (варіант 4)\n");

        Scanner scanner = new Scanner(System.in);
        ExecutorService executor = null;

        // Питання про кількість потоків
        System.out.print("Скільки задіяти потоків? Enter - вибрати автоматично: ");
        String line = scanner.nextLine().trim();
        if (!line.isEmpty()) {
            try {
                int n = Integer.parseInt(line);
                if (n > 0) {
                    executor = Executors.newFixedThreadPool(n);
                    System.out.println("Використовується пул з " + n + " потоків.");
                } else {
                    System.out.println("Невірне число потоків, буде використано автоматично (common pool).");
                }
            } catch (NumberFormatException e) {
                System.out.println("Не наведено коректне число, буде використано автоматично (common pool).");
            }
        } else {
            System.out.println("Використовується автоматичне середовище (ForkJoinPool.commonPool).");
        }

        // --- Підготуємо (перезапишемо) текстові файли у робочій директорії:
        prepareTextFiles();

        // ----------------- ЧАСТИНА 1 -----------------
        System.out.println("\n---- Частина 1: Асинхронне читання та обробка файлів ----");
        String[] files = {"Text1.txt", "Text2.txt", "Text3.txt"};

        Instant part1Start = Instant.now();

        // Зберігаємо futures, які повертають FileResult (щоб потім з'єднати processed з усіх)
        List<CompletableFuture<FileResult>> fileFutures = new ArrayList<>();
        // Окремо futures для друку/логування (Void)
        List<CompletableFuture<Void>> printFutures = new ArrayList<>();

        for (String filename : files) {
            final long startNano = System.nanoTime();

            // Читання та обробка -> повертає FileResult
            CompletableFuture<FileResult> fileFuture;
            if (executor == null) {
                fileFuture = CompletableFuture
                        .supplyAsync(() -> {
                            try {
                                return Files.readString(Path.of(filename), StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        })
                        .thenApplyAsync(content -> {
                            long afterReadNano = System.nanoTime();
                            long readMs = (afterReadNano - startNano) / 1_000_000;

                            // Видаляємо усі буквені символи Unicode (обидві абетки)
                            String noLetters = content.replaceAll("\\p{L}", "");
                            List<Character> chars = noLetters.chars()
                                    .mapToObj(c -> (char) c)
                                    .collect(Collectors.toList());

                            long afterProcessNano = System.nanoTime();
                            long totalMs = (afterProcessNano - startNano) / 1_000_000;

                            return new FileResult(filename, content, chars, readMs, totalMs);
                        });
            } else {
                fileFuture = CompletableFuture
                        .supplyAsync(() -> {
                            try {
                                return Files.readString(Path.of(filename), StandardCharsets.UTF_8);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }, executor)
                        .thenApplyAsync(content -> {
                            long afterReadNano = System.nanoTime();
                            long readMs = (afterReadNano - startNano) / 1_000_000;

                            String noLetters = content.replaceAll("\\p{L}", "");
                            List<Character> chars = noLetters.chars()
                                    .mapToObj(c -> (char) c)
                                    .collect(Collectors.toList());

                            long afterProcessNano = System.nanoTime();
                            long totalMs = (afterProcessNano - startNano) / 1_000_000;

                            return new FileResult(filename, content, chars, readMs, totalMs);
                        }, executor);
            }

            fileFutures.add(fileFuture);

            // А тепер окремо асинхронно виводимо info для цього файлу
            CompletableFuture<Void> printFuture;
            if (executor == null) {
                printFuture = fileFuture.thenAcceptAsync(fr -> {
                    System.out.println("\n[Файл] " + fr.filename);
                    System.out.println("Початковий текст: " + fr.original);
                    System.out.println("Результуючий масив (символи без букв): " + fr.processed);
                    System.out.println("Час читання (ms): " + fr.readMs + "; Загальний час (ms): " + fr.totalMs);
                }).thenRunAsync(() -> System.out.println("Завершено обробку файлу: " + filename));
            } else {
                printFuture = fileFuture.thenAcceptAsync(fr -> {
                    System.out.println("\n[Файл] " + fr.filename);
                    System.out.println("Початковий текст: " + fr.original);
                    System.out.println("Результуючий масив (символи без букв): " + fr.processed);
                    System.out.println("Час читання (ms): " + fr.readMs + "; Загальний час (ms): " + fr.totalMs);
                }, executor).thenRunAsync(() -> System.out.println("Завершено обробку файлу: " + filename), executor);
            }

            printFutures.add(printFuture);
        }

        // Коли всі fileFutures завершені — з'єднаємо їх processed у один масив
        CompletableFuture<List<Character>> merged = CompletableFuture
                .allOf(fileFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v ->
                        fileFutures.stream()
                                .map(CompletableFuture::join) // safe because allOf completed
                                .flatMap(fr -> fr.processed.stream())
                                .collect(Collectors.toList())
                );

        // Асинхронно запишемо цей загальний масив у файл і виведемо його на екран
        CompletableFuture<Void> writeAndPrintMerged;
        if (executor == null) {
            writeAndPrintMerged = merged.thenAcceptAsync(list -> {
                // Формуємо рядок для запису
                String contentToWrite = list.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("", "[", "]"));

                long writeStart = System.nanoTime();
                try {
                    Files.writeString(Path.of("ResultArray.txt"), contentToWrite, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                long writeMs = (System.nanoTime() - writeStart) / 1_000_000;

                System.out.println("\n[Результуючий масив (з усіх файлів)] : " + contentToWrite);
                System.out.println("Файл 'ResultArray.txt' записаний асинхронно. Час запису (ms): " + writeMs);
            }) ;
        } else {
            writeAndPrintMerged = merged.thenAcceptAsync(list -> {
                String contentToWrite = list.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining("", "[", "]"));

                long writeStart = System.nanoTime();
                try {
                    Files.writeString(Path.of("ResultArray.txt"), contentToWrite, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                long writeMs = (System.nanoTime() - writeStart) / 1_000_000;

                System.out.println("\n[Результуючий масив (з усіх файлів)] : " + contentToWrite);
                System.out.println("Файл 'ResultArray.txt' записаний асинхронно. Час запису (ms): " + writeMs);
            }, executor);
        }

        // Об'єднуємо всі CompletableFuture у один список (для allOf)
        List<CompletableFuture<?>> allPart1Tasks = new ArrayList<>();
        allPart1Tasks.addAll(printFutures);
        allPart1Tasks.add(writeAndPrintMerged);

        // allOf приймає тільки один масив Future'ів
        CompletableFuture<Void> part1All = CompletableFuture.allOf(allPart1Tasks.toArray(new CompletableFuture<?>[0]));

        // Можемо вивести лог, що завдання запущено (демонстрація runAsync)
        CompletableFuture<Void> logger;
        if (executor == null) {
            logger = CompletableFuture.runAsync(() -> System.out.println("\n(Лог) Асинхронні задачі зчитування та обробки запущені..."));
        } else {
            logger = CompletableFuture.runAsync(() -> System.out.println("\n(Лог) Асинхронні задачі зчитування та обробки запущені..."), executor);
        }

        // Чекаємо: logger та всі задачі частини 1
        CompletableFuture.allOf(part1All, logger).join();

        Instant part1End = Instant.now();
        System.out.println("\nЧас завершення всіх асинхронних операцій (частина 1): " +
                Duration.between(part1Start, part1End).toMillis() + " ms\n");

        // --- ОЧІКУВАННЯ ENTER МІЖ ЧАСТИНАМИ ---
        System.out.println("Натисніть Enter щоб продовжити до Частини 2...");
        scanner.nextLine();

        // ----------------- ЧАСТИНА 2 -----------------
        System.out.println("\n---- Частина 2: Генерація послідовності і обчислення суми суміжних добутків ----");
        Instant part2Start = Instant.now();

        // 1) генерація послідовності випадкових дійсних чисел (supplyAsync) -> SequenceResult
        CompletableFuture<SequenceResult> seqFuture;
        if (executor == null) {
            seqFuture = CompletableFuture.supplyAsync(() -> {
                long startNano = System.nanoTime();
                Random rnd = new Random();
                int n = 20;
                double[] arr = new double[n];
                for (int i = 0; i < n; i++) {
                    arr[i] = -10 + rnd.nextDouble() * 20;
                }
                // невелика затримка, щоб було помітно асинхронність
                try { Thread.sleep(80); } catch (InterruptedException ignored) {}
                long genMs = (System.nanoTime() - startNano) / 1;
                return new SequenceResult(arr, genMs);
            });
        } else {
            seqFuture = CompletableFuture.supplyAsync(() -> {
                long startNano = System.nanoTime();
                Random rnd = new Random();
                int n = 20;
                double[] arr = new double[n];
                for (int i = 0; i < n; i++) {
                    arr[i] = -10 + rnd.nextDouble() * 20;
                }
                try { Thread.sleep(80); } catch (InterruptedException ignored) {}
                long genMs = (System.nanoTime() - startNano) / 1;
                return new SequenceResult(arr, genMs);
            }, executor);
        }

        // 2) обчислення суми a1*a2 + a2*a3 + ... (thenApplyAsync) -> ComputationResult
        CompletableFuture<ComputationResult> compFuture;
        if (executor == null) {
            compFuture = seqFuture.thenApplyAsync(sr -> {
                long startNano = System.nanoTime();
                double sum = 0.0;
                double[] arr = sr.arr;
                for (int i = 0; i < arr.length - 1; i++) {
                    sum += arr[i] * arr[i + 1];
                }
                long computeMs = (System.nanoTime() - startNano) / 1;
                return new ComputationResult(sum, computeMs);
            });
        } else {
            compFuture = seqFuture.thenApplyAsync(sr -> {
                long startNano = System.nanoTime();
                double sum = 0.0;
                double[] arr = sr.arr;
                for (int i = 0; i < arr.length - 1; i++) {
                    sum += arr[i] * arr[i + 1];
                }
                long computeMs = (System.nanoTime() - startNano) / 1;
                return new ComputationResult(sum, computeMs);
            }, executor);
        }

        // 3) вивід послідовності (thenAcceptAsync) та потім вивід результату (thenCompose -> thenAcceptAsync)
        CompletableFuture<Void> printSeqAndResult;
        if (executor == null) {
            printSeqAndResult = seqFuture.thenAcceptAsync(sr -> {
                System.out.println("Початкова послідовність (n = " + sr.arr.length + "), час генерації (ms): " + sr.genMs);
                System.out.println(Arrays.toString(sr.arr));
            }).thenCompose(v -> compFuture.thenAcceptAsync(cr -> {
                System.out.println("Результат обчислення a1*a2 + a2*a3 + ... + a_{n-1}*a_n = " + cr.sum);
                System.out.println("Час обчислення (ns): " + cr.computeMs);
            })).thenRunAsync(() -> System.out.println("Завершено обчислення (частина 2)."));
        } else {
            ExecutorService finalExecutor = executor;
            printSeqAndResult = seqFuture.thenAcceptAsync(sr -> {
                System.out.println("Початкова послідовність (n = " + sr.arr.length + "), час генерації (ms): " + sr.genMs);
                System.out.println(Arrays.toString(sr.arr));
            }, executor).thenCompose(v -> compFuture.thenAcceptAsync(cr -> {
                System.out.println("Результат обчислення a1*a2 + a2*a3 + ... + a_{n-1}*a_n = " + cr.sum);
                System.out.println("Час обчислення (ns): " + cr.computeMs);
            }, finalExecutor)).thenRunAsync(() -> System.out.println("Завершено обчислення (частина 2)."), finalExecutor);
        }

        // Чекаємо завершення усіх задач частини 2
        printSeqAndResult.join();

        Instant part2End = Instant.now();
        System.out.println("\nЧас виконання усіх асинхронних операцій (частина 2): " +
                Duration.between(part2Start, part2End).toMillis() + " ms");

        System.out.println("\nВсі частини завершено.");

        // Завершуємо екзек'ютор
        if (executor != null) {
            executor.shutdown();
        }

        // Закриваємо сканер
        scanner.close();
    }

    // Метод для підготовки файлів (перезаписує вміст)
    private static void prepareTextFiles() {
        Map<String, String> filesContent = new LinkedHashMap<>();
        filesContent.put("Text1.txt", "Hello, world! Today is a sunny day. And i wanna say my cat is gay?!?!?! Що він несе.....");
        filesContent.put("Text2.txt", "Числовий тестовий рядок: 12345, число пі 3.1415, рандомне 2,252, ну це база E=mc^2");
        filesContent.put("Text3.txt", "Тестовий рядок: АаБбВвГгДд, які далі літери? !№;%:?*()[]\\/ ., а це символи були");

        filesContent.forEach((name, content) -> {
            try {
                Files.writeString(Path.of(name), content, StandardCharsets.UTF_8);
                System.out.println("(Файл створено/оновлено) " + name);
            } catch (IOException e) {
                System.err.println("Не вдалося записати файл " + name + ": " + e.getMessage());
            }
        });
        System.out.println();
    }
}
