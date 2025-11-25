import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArraySet;

public class Main {

    // Callable, який отримує масив дробових чисел і повертає масив їх квадратів
    static class SquareArrayTask implements Callable<double[]> {
        private final double[] input;
        // невелика затримка всередині, щоб видно було стани isDone() — можна змінити або прибрати
        private final long perElementMillis;

        public SquareArrayTask(double[] input, long perElementMillis) {
            this.input = input;
            this.perElementMillis = perElementMillis;
        }

        @Override
        public double[] call() throws Exception {
            double[] out = new double[input.length];
            for (int i = 0; i < input.length; i++) {
                // Перевіряємо чи потік був перерваний
                if (Thread.currentThread().isInterrupted()) {
                    // акуратно відповідаємо на переривання
                    throw new InterruptedException("Task was interrupted");
                }
                double v = input[i];
                out[i] = v * v;
                // невелика імітація більш тривалої роботи — щоб було видно isDone() у прикладі
                if (perElementMillis > 0) {
                    try {
                        Thread.sleep(perElementMillis);
                    } catch (InterruptedException ie) {
                        // встановлюємо прапорець і передаємо далі
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                }
            }
            return out;
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        try {
            System.out.println("Діапазон значень буде обмежено [0.5, 99.5]. Якщо введете інше — буде приведено до меж.");
            System.out.print("Введіть мінімальне значення (double), або Enter для 0.5: ");
            String minLine = sc.nextLine().trim();
            double min = minLine.isEmpty() ? 0.5 : Double.parseDouble(minLine);

            System.out.print("Введіть максимальне значення (double), або Enter для 99.5: ");
            String maxLine = sc.nextLine().trim();
            double max = maxLine.isEmpty() ? 99.5 : Double.parseDouble(maxLine);

            // Примусово обмежимо діапазон (вимога: [0.5;99.5])
            if (min < 0.5) { System.out.println("min < 0.5 — приведено до 0.5"); min = 0.5; }
            if (max > 99.5) { System.out.println("max > 99.5 — приведено до 99.5"); max = 99.5; }
            if (min > max) {
                System.out.println("min > max — міняю місцями.");
                double t = min; min = max; max = t;
            }

            System.out.print("Введіть кількість елементів масиву (ціле від 40 до 60), або Enter для 50: ");
            String nLine = sc.nextLine().trim();
            int n = nLine.isEmpty() ? 50 : Integer.parseInt(nLine);
            if (n < 40) { System.out.println("Кількість менше 40 — встановлено 40"); n = 40; }
            if (n > 60) { System.out.println("Кількість більше 60 — встановлено 60"); n = 60; }

            // Параметри розбиття
            final int chunkSize = 10; // розмір частини (можна змінити)
            final int chunks = (n + chunkSize - 1) / chunkSize; // обчислення кількості частин

            // Генеруємо масив випадкових дробових чисел у [min, max)
            double[] array = new double[n];
            Random rnd = new Random();
            for (int i = 0; i < n; i++) {
                array[i] = min + rnd.nextDouble() * (max - min);
            }

            System.out.printf("Згенеровано масив з %d елементів у діапазоні [%.3f, %.3f]%n", n, min, max);
            System.out.printf("Розбито на %d частин по ~%d елементів%n", chunks, chunkSize);

            // Колекція CopyOnWriteArraySet для зберігання унікальних квадратів (потокобезпечно)
            CopyOnWriteArraySet<Double> resultSet = new CopyOnWriteArraySet<>();

            // Thread pool — по кількості частин (щоб кожна частина могла виконатися власним потоком)
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(chunks, Runtime.getRuntime().availableProcessors()));

            List<Future<double[]>> futures = new ArrayList<>();
            long perElementMillis = 30; // невелика імітація обробки для демонстрації isDone() (можна змінити)
            long startTime = System.nanoTime();

            // Розбиття на чанки
            for (int i = 0; i < chunks; i++) {
                int from = i * chunkSize;
                int to = Math.min(from + chunkSize, n);
                double[] sub = Arrays.copyOfRange(array, from, to);
                SquareArrayTask task = new SquareArrayTask(sub, perElementMillis); // cтворення окремого callable для кожної частини
                Future<double[]> f = executor.submit(task); // передача кожної частини в окремий потік
                futures.add(f);
            }

            // Моніторинг статусів futures (виводимо періодично isDone() та isCancelled())
            System.out.println("\nСтатуси задач (періодично оновлюються):");
            boolean allDone = false;
            while (!allDone) {
                allDone = true;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < futures.size(); i++) {
                    Future<double[]> f = futures.get(i);
                    boolean done = f.isDone();
                    boolean cancelled = f.isCancelled();
                    sb.append(String.format("Task %d: done=%b, cancelled=%b;  ", i, done, cancelled));
                    if (!done && !cancelled) allDone = false;
                }
                System.out.println(sb.toString());
                if (!allDone) {
                    try {
                        Thread.sleep(200); // пауза між перевірками
                    } catch (InterruptedException e) {
                        // якщо main-thread перерваний — припиняємо
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            // Збираємо результати через Future.get(), перевіряючи isCancelled()
            AtomicInteger totalCollected = new AtomicInteger(0);
            for (int i = 0; i < futures.size(); i++) {
                Future<double[]> f = futures.get(i);
                if (f.isCancelled()) {
                    System.out.printf("Task %d була скасована, пропускаю її результат.%n", i);
                    continue;
                }
                try {
                    double[] res = f.get(); // тут вже точно done або кинута помилка
                    for (double v : res) {
                        resultSet.add(v); // автоматичне boxing в Double
                        totalCollected.incrementAndGet();
                    }
                } catch (ExecutionException ee) {
                    System.out.printf("Task %d завершилась з помилкою: %s%n", i, ee.getCause());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.out.printf("Отримання результатів перервано.%n");
                    break;
                }
            }

            long endTime = System.nanoTime();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

            // Виводи результатів
            System.out.println("\nРезультати збережено в CopyOnWriteArraySet (унікальні квадрати):");
            System.out.printf("Загальна кількість зібраних квадратів (включно з дублями): %d%n", totalCollected.get());
            System.out.printf("Розмір CopyOnWriteArraySet (унікальних значень): %d%n", resultSet.size());

            // Покажемо декілька прикладів (перші 10) — для наочності:
            System.out.println("Перші до 10 елементів set (не гарантовано у первинному порядку):");
            int shown = 0;
            for (Double d : resultSet) {
                System.out.printf("%.6f  ", d);
                if (++shown >= 10) break;
            }
            System.out.println();

            // Завершальний рядок — час роботи програми (вимога)
            System.out.printf("Час роботи програми: %d ms%n", elapsedMs);

            System.out.print("\nВивести повну інформацію про масиви? (Y/N): ");
            String ans = sc.nextLine().trim().toUpperCase();

            if (ans.equals("Y")) {

                // Початковий масив
                StringBuilder sbInit = new StringBuilder("[ ");
                for (int i = 0; i < array.length; i++) {
                    sbInit.append(String.format("%.6f", array[i]));
                    if (i < array.length - 1) sbInit.append(" | ");
                }
                sbInit.append(" ]");

                // Кінцевий масив квадратів — у тому ж порядку, що й початковий
                StringBuilder sbFinal = new StringBuilder("[ ");
                for (double v : array) {
                    sbFinal.append(String.format("%.6f", v * v));
                    sbFinal.append(" | ");
                }
                // прибираємо останнє " | "
                sbFinal.setLength(sbFinal.length() - 3);
                sbFinal.append(" ]");

                System.out.println("\n=== Початковий масив ===");
                System.out.println(sbInit);

                System.out.println("\n=== Масив квадратів (кінцевий) ===");
                System.out.println(sbFinal);
            }

            System.out.println("\nПрограма завершена.");


            // Коректне завершення executor'а
            executor.shutdownNow();
        } catch (NumberFormatException nfe) {
            System.err.println("Невірний формат введених даних. Запустіть програму ще раз і введіть числа у правильному форматі.");
        } finally {
            sc.close();
        }
    }
}
