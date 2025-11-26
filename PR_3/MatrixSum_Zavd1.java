import java.util.*;
import java.util.concurrent.*;
import java.io.*;

public class MatrixSum_Zavd1 {

    private static final Scanner scanner = new Scanner(System.in);

    // ----------------------------- Зчитування чисел -----------------------------
    private static int readAnyInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (Exception e) {
                System.out.println("Невірне число. Спробуйте ще.");
            }
        }
    }

    private static int[] readMatrixSize(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim().replace(" ", "");
            if (!line.contains("*")) {
                System.out.println("Невірний формат. Приклад: 500*1000");
                continue;
            }
            try {
                String[] parts = line.split("\\*");
                int r = Integer.parseInt(parts[0]);
                int c = Integer.parseInt(parts[1]);
                if (r > 0 && c > 0)
                    return new int[]{r, c};
            } catch (Exception ignored) {}
            System.out.println("Помилка. Приклад коректного вводу: 250*500");
        }
    }

    // ----------------------------- Вивід матриці -----------------------------

    // Знаходження максимального числа символів у будь-якому елементі матриці
    // ---------------------------------------------------------------------
        private static int getMaxNumberWidth(int[][] m) {
            int max = 0;
            for (int[] row : m) {
                for (int value : row) {
                    int len = String.valueOf(value).length();
                    if (len > max) max = len;
                }
            }
            return max;
        }

        // Вивід матриці у консоль з вирівнюванням по ширині
        private static void printMatrix(int[][] m) {
            int width = getMaxNumberWidth(m); // максимальна кількість символів числа

            for (int[] row : m) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < row.length; j++) {

                    // форматування: число вирівнюється справа, займаючи "width" символів
                    sb.append(String.format("%" + width + "d", row[j]));

                    if (j < row.length - 1) sb.append(" | ");
                }
                System.out.println(sb);
            }
        }


        // Формування вирівняного тексту матриці (для запису у файл)
        private static String matrixToString(int[][] m) {
            int width = getMaxNumberWidth(m);
            StringBuilder out = new StringBuilder();

            for (int[] row : m) {
                for (int j = 0; j < row.length; j++) {
                    out.append(String.format("%" + width + "d", row[j]));
                    if (j < row.length - 1) out.append(" | ");
                }
                out.append("\n");
            }

            return out.toString();
        }


    // ----------------------------- Work Stealing -----------------------------
    static class ColumnSumTask extends RecursiveTask<long[]> {
        private static final int THRESHOLD = 4;
        final int[][] matrix;
        final int start, end;

        ColumnSumTask(int[][] matrix, int start, int end) {
            this.matrix = matrix;
            this.start = start;
            this.end = end;
        }

        @Override
        protected long[] compute() {
            int len = end - start;

            if (len <= THRESHOLD) {
                long[] res = new long[len];
                for (int i = 0; i < len; i++) {
                    long s = 0;
                    int col = start + i;
                    for (int[] row : matrix) s += row[col];
                    res[i] = s;
                }
                return res;
            }

            int mid = start + len / 2;
            ColumnSumTask left = new ColumnSumTask(matrix, start, mid);
            ColumnSumTask right = new ColumnSumTask(matrix, mid, end);

            left.fork();
            long[] rightRes = right.compute();
            long[] leftRes = left.join();

            long[] finalRes = new long[leftRes.length + rightRes.length];
            System.arraycopy(leftRes, 0, finalRes, 0, leftRes.length);
            System.arraycopy(rightRes, 0, finalRes, leftRes.length, rightRes.length);

            return finalRes;
        }
    }

    // ----------------------------- Work Dealing -----------------------------
    static long[] sumsWorkDealing(int[][] matrix, int workerCount) throws Exception {
        int cols = matrix[0].length;
        long[] result = new long[cols];

        List<List<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < workerCount; i++) tasks.add(new ArrayList<>());

        for (int c = 0; c < cols; c++) tasks.get(c % workerCount).add(c);

        ExecutorService ex = Executors.newFixedThreadPool(workerCount);
        CountDownLatch latch = new CountDownLatch(workerCount);

        for (List<Integer> lst : tasks) {
            ex.submit(() -> {
                for (int col : lst) {
                    long s = 0;
                    for (int[] row : matrix) s += row[col];
                    result[col] = s;
                }
                latch.countDown();
            });
        }

        latch.await();
        ex.shutdown();
        return result;
    }

    // ----------------------------- ФАЙЛИ -----------------------------
    private static void writeToFile(String filename, String content) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.print(content);
        } catch (Exception e) {
            System.out.println("Помилка запису у файл " + filename);
        }
    }

    // ----------------------------- MAIN -----------------------------
    public static void main(String[] args) throws Exception {

        System.out.println("=== Обчислення сум стовпців матриці (Варіант №4) ===");

        int[] size = readMatrixSize("~\np.s Я не став обмежувати максимальний розмір матриці,\nадже мій ноутбук здатен створити та прорахувати матрицю 20.000*20.000,\nчийсь компьютер виконає і 100.000*100.000, чийсь і більше.\nАле, звісно, обмежив мінімальний розмір.\n~\nВведіть розмір матриці (наприклад 500*1000): ");
        int rows = size[0], cols = size[1];

        int min = readAnyInt("Введіть мінімальне значення елементів: ");
        int max = readAnyInt("Введіть максимальне значення елементів: ");

        if (min > max) { int t = min; min = max; max = t; }

        int[][] matrix = new int[rows][cols];
        Random rnd = new Random();
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                matrix[i][j] = rnd.nextInt(max - min + 1) + min;

        System.out.println("\nМатриця створена!");
        System.out.println("Готові? Натисніть Enter...");
        scanner.nextLine();

        // -------------------- Work Stealing --------------------
        ForkJoinPool fj = new ForkJoinPool();
        long t0 = System.nanoTime();
        long[] stealing = fj.invoke(new ColumnSumTask(matrix, 0, cols));
        long t1 = System.nanoTime();
        fj.shutdown();

        double stealTime = (t1 - t0) / 1_000_000.0;
        System.out.printf("Work Stealing виконано за %.3f ms%n", stealTime);

        System.out.println("Натисніть Enter для Work Dealing...");
        scanner.nextLine();

        // -------------------- Work Dealing --------------------
        long t2 = System.nanoTime();
        long[] dealing = sumsWorkDealing(matrix, Runtime.getRuntime().availableProcessors());
        long t3 = System.nanoTime();

        double dealTime = (t3 - t2) / 1_000_000.0;

        System.out.printf("Work Dealing виконано за %.3f ms%n", dealTime);
        System.out.println("Натисніть Enter, щоб порівняти...");
        scanner.nextLine();

        // -------------------- Порівняння --------------------
        boolean stealFaster = stealTime < dealTime;

        String green = "\033[1;32m";
        String reset = "\033[0m";

        System.out.println("\n========== ПОРІВНЯННЯ ==========\n");

        System.out.printf("Work Stealing: %.3f ms %s%n",
                stealTime, stealFaster ? green + "<– швидше" + reset : "");

        System.out.printf("Work Dealing: %.3f ms %s%n",
                dealTime, !stealFaster ? green + "<– швидше" + reset : "");

        System.out.println("\n================================\n");

        // -------------------- Запис у файли --------------------
        writeToFile("Matrix.txt", matrixToString(matrix));

        StringBuilder results = new StringBuilder();

        results.append("Суми елементів усіх стовпців матриці:\n");
        for (int i = 0; i < stealing.length; i++) {
            results.append(stealing[i]);
            if (i < stealing.length - 1) results.append(" | ");
        }
        results.append("\n\n");

        results.append("========== ПОРІВНЯННЯ ==========\n\n");

        results.append(String.format("Work Stealing: %.3f ms %s%n",
                stealTime, stealFaster ? "<– швидше" : ""));

        results.append(String.format("Work Dealing: %.3f ms %s%n",
                dealTime, !stealFaster ? "<– швидше" : ""));

        results.append("\n================================\n");

        writeToFile("MatrixResults.txt", results.toString());

        System.out.println("Результати записано у файли Matrix.txt та MatrixResults.txt.");

        // -------------------- Додатковий вивід --------------------
        System.out.print("\nХочете вивести матрицю? Y / N: ");
        if (scanner.nextLine().trim().equalsIgnoreCase("Y")) {
            System.out.println("\nМатриця:");
            printMatrix(matrix);
        }

        System.out.print("\nХочете вивести результати? Y / N: ");
        if (scanner.nextLine().trim().equalsIgnoreCase("Y")) {
            System.out.println("\n" + results);
        }
    }
}
