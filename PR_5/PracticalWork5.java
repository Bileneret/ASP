import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PracticalWork5 {

    // Діапазон для пошуку простих чисел (Завдання 1)
    private static final int RANGE_START = 1;
    private static final int RANGE_END = 10_000_000;

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        System.out.println("Кількість ядер CPU: " + Runtime.getRuntime().availableProcessors());

        // =================================================================================
        // ЗАВДАННЯ №1: Пошук простих чисел у двох діапазонах (thenCombine)
        // =================================================================================
        System.out.println("\n--- Завдання 1: thenCombine() (Статистика по підзавданнях) ---");
        performPrimeCountingTaskWithStats();

        Thread.sleep(1500); // Пауза для розділення виводу

        // =================================================================================
        // ЗАВДАННЯ №2: Аналіз та вибір ПЗ (allOf)
        // =================================================================================
        System.out.println("\n--- Завдання 2: allOf() та вибір кращого з 5 варіантів ПЗ ---");
        chooseBestSoftwareFromFive();

    }

    /**
     * ЗАВДАННЯ 1:
     * 1. Розбиває діапазон на дві частини.
     * 2. Рахує прості числа та заміряє час виконання окремо для кожної частини.
     * 3. Використовує thenCombine для об'єднання результатів та підсумку часу.
     */
    private static void performPrimeCountingTaskWithStats() throws ExecutionException, InterruptedException {
        int splitPoint = RANGE_END / 2;
        long startTimeGlobal = System.nanoTime(); // Для заміру загального "стінного" часу

        // --- Підзавдання 1 ---
        CompletableFuture<TaskResult> task1 = CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            long count = countPrimes(RANGE_START, splitPoint);
            long end = System.nanoTime();
            // Повертаємо об'єкт з результатом і часом
            return new TaskResult("Підзавдання 1", count, end - start);
        });

        // --- Підзавдання 2 ---
        CompletableFuture<TaskResult> task2 = CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            long count = countPrimes(splitPoint + 1, RANGE_END);
            long end = System.nanoTime();
            return new TaskResult("Підзавдання 2", count, end - start);
        });

        // --- ОБ'ЄДНАННЯ (thenCombine) ---
        // Тут ми приймаємо результати обох задач (res1, res2) і формуємо загальний звіт
        CompletableFuture<String> combinedTask = task1.thenCombine(task2, (res1, res2) -> {
            System.out.println("\n>>> Деталі виконання:");
            System.out.println(res1);
            System.out.println(res2);

            // Сумуємо кількість
            long totalCount = res1.count + res2.count;

            // Сумуємо час (CPU time) - скільки процесор працював сумарно
            double totalCpuTimeMs = (res1.durationNs + res2.durationNs) / 1_000_000.0;

            return String.format("ВСЬОГО знайдено: %d простих чисел.\n   Сумарний час роботи потоків (CPU time): %.2f мс",
                    totalCount, totalCpuTimeMs);
        });

        // Очікуємо результат та точний час
        String finalReport = combinedTask.get();
        long endTimeGlobal = System.nanoTime();
        double wallClockTimeMs = (endTimeGlobal - startTimeGlobal) / 1_000_000.0;

        System.out.println("------------------------------------------------");
        System.out.println(finalReport);
        // Час, який реально пройшов для користувача (має бути меншим за суму часів потоків, бо паралельно)
        System.out.printf("   Реальний час очікування (Wall-clock):  %.2f мс%n", wallClockTimeMs);
        System.out.println("------------------------------------------------");
    }

    // Завдання №2
    private static void chooseBestSoftwareFromFive() {
        List<String> softwareNames = List.of(
                "Software A (Lite)",
                "Software B (Basic)",
                "Software C (Enterprise)",
                "Software D (Pro)",
                "Software E (Ultimate)"
        );

        List<CompletableFuture<SoftwareScore>> futures = new ArrayList<>();

        // Створюємо завдання
        for (String name : softwareNames) {
            futures.add(analyzeSoftwareAsync(name));
        }

        CompletableFuture<?>[] futuresArray = futures.toArray(new CompletableFuture[0]);

        // Чекаємо (allOf)
        CompletableFuture.allOf(futuresArray).thenAccept(v -> {
            List<SoftwareScore> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            System.out.println("\n--- Підсумкова таблиця (5 варіантів) ---");
            SoftwareScore bestOption = null;

            for (SoftwareScore score : results) {
                System.out.println(score);
                if (bestOption == null || score.calculateTotalScore() > bestOption.calculateTotalScore()) {
                    bestOption = score;
                }
            }

            System.out.println("\n------------------------------------------------");
            System.out.println("ПЕРЕМОЖЕЦЬ: " + bestOption.name + " з рейтингом " + String.format("%.1f", bestOption.calculateTotalScore()));
            System.out.println("------------------------------------------------");
        }).join();
    }


    // Клас для збереження результату Завдання 1 (Кількість + Час)
    static class TaskResult {
        String taskName;
        long count;
        long durationNs;

        public TaskResult(String taskName, long count, long durationNs) {
            this.taskName = taskName;
            this.count = count;
            this.durationNs = durationNs;
        }

        @Override
        public String toString() {
            return String.format("   [%s] Знайдено: %5d | Час: %6.2f мс",
                    taskName, count, durationNs / 1_000_000.0);
        }
    }

    // Математика для пошуку і підрахування простих чисел
    private static long countPrimes(int start, int end) {
        return IntStream.rangeClosed(start, end)
                .filter(PracticalWork5::isPrime)
                .count();
    }

    private static boolean isPrime(int number) {
        if (number <= 1) return false;
        if (number == 2 || number == 3) return true;
        if (number % 2 == 0) return false;
        // Оптимізована перевірка
        for (int i = 3; i * i <= number; i += 2) {
            if (number % i == 0) return false;
        }
        return true;
    }

    // Асинхронний аналіз для Завдання 2
    private static CompletableFuture<SoftwareScore> analyzeSoftwareAsync(String softwareName) {
        CompletableFuture<Integer> priceTask = CompletableFuture.supplyAsync(() -> {
            int price = ThreadLocalRandom.current().nextInt(100, 1000);
            sleep(ThreadLocalRandom.current().nextInt(100, 500));
            return price;
        });

        CompletableFuture<Integer> funcTask = CompletableFuture.supplyAsync(() -> {
            int score = ThreadLocalRandom.current().nextInt(3, 11);
            sleep(ThreadLocalRandom.current().nextInt(100, 500));
            return score;
        });

        CompletableFuture<Integer> supportTask = CompletableFuture.supplyAsync(() -> {
            int score = ThreadLocalRandom.current().nextInt(3, 11);
            sleep(ThreadLocalRandom.current().nextInt(100, 500));
            return score;
        });

        return CompletableFuture.allOf(priceTask, funcTask, supportTask)
                .thenApply(v -> new SoftwareScore(softwareName, priceTask.join(), funcTask.join(), supportTask.join()));
    }

    // Клас результату для Завдання 2
    static class SoftwareScore {
        String name;
        int price;
        int functionality;
        int support;

        public SoftwareScore(String name, int price, int functionality, int support) {
            this.name = name;
            this.price = price;
            this.functionality = functionality;
            this.support = support;
        }

        public double calculateTotalScore() {
            return (functionality + support) * 10 - (price / 20.0);
        }

        @Override
        public String toString() {
            return String.format("%-25s | Ціна: $%4d | функціональність: %2d | Підтримка: %2d | Рейтинг: %5.1f",
                    name, price, functionality, support, calculateTotalScore());
        }
    }

    private static void sleep(int millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}