# Практична робота №4 — CompletableFuture (варіант 4)

Програма демонструє асинхронну обробку файлів і послідовностей у Java з використанням `CompletableFuture`, пулу потоків, асинхронних методів (`supplyAsync`, `thenApplyAsync`, `thenAcceptAsync`, `thenRunAsync`) та вимірювання часу виконання.

Користувач вказує кількість потоків (або використовується автоматичний пул). У першій частині асинхронно читаються і обробляються текстові файли (видалення букв, збір символів). У другій — генерується послідовність чисел і обчислюється сума добутків суміжних елементів.

---

# Загальна структура програми

1.  [Отримання кількості потоків](#1-отримання-кількості-потоків-від-користувача)
2.  [Підготовка текстових файлів](#2-підготовка-текстових-файлів)
3.  [Частина 1: Асинхронне читання та обробка файлів](#3-частина-1-асинхронне-читання-та-обробка-файлів)
4.  [Об'єднання результатів обробки файлів](#4-обєднання-результатів-обробки-файлів)
5.  [Асинхронний запис і вивід об'єднаного масиву](#5-асинхронний-запис-і-вивід-обєднаного-масиву)
6.  [Моніторинг і чекання завершення частини 1](#6-моніторинг-і-чекання-завершення-частини-1)
7.  [Очікування переходу до частини 2](#7-очікування-переходу-до-частини-2)
8.  [Частина 2: Генерація послідовності](#8-частина-2-генерація-послідовності)
9.  [Обчислення суми добутків](#9-обчислення-суми-добутків)
10. [Вивід результатів частини 2](#10-вивід-результатів-частини-2)

Нижче кожен етап окремо.

---

# 1. Отримання кількості потоків від користувача

[↑Нагору↑](#Загальна-структура-програми)

На початку програма запитує у користувача кількість потоків для пулу. Якщо не вказано, використовується автоматичний пул (`ForkJoinPool.commonPool`).

Фрагмент коду:

```java
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
```

Пояснення: Якщо вказано позитивне число, створюється фіксований пул потоків. Оброблюються помилки вводу.

---

# 2. Підготовка текстових файлів

[↑Нагору↑](#Загальна-структура-програми)

Програма створює або перезаписує три текстові файли з різним вмістом для демонстрації обробки.

Фрагмент коду:

```java
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
```

Пояснення: Файли записуються з кодуванням UTF-8 для підтримки кирилиці. Оброблюються помилки запису.

---

# 3. Частина 1: Асинхронне читання та обробка файлів

[↑Нагору↑](#Загальна-структура-програми)

Для кожного файлу створюються `CompletableFuture` для асинхронного читання та обробки (видалення букв).

Фрагмент коду:

```java
for (String filename : files) {
    final long startNano = System.nanoTime();

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

                    String noLetters = content.replaceAll("\\p{L}", "");
                    List<Character> chars = noLetters.chars()
                            .mapToObj(c -> (char) c)
                            .collect(Collectors.toList());

                    long afterProcessNano = System.nanoTime();
                    long totalMs = (afterProcessNano - startNano) / 1_000_000;

                    return new FileResult(filename, content, chars, readMs, totalMs);
                });
    } else {
        // Аналогічно з executor
    }

    fileFutures.add(fileFuture);

    // Асинхронний вивід інформації про файл
    CompletableFuture<Void> printFuture;
    if (executor == null) {
        printFuture = fileFuture.thenAcceptAsync(fr -> {
            System.out.println("\n[Файл] " + fr.filename);
            System.out.println("Початковий текст: " + fr.original);
            System.out.println("Результуючий масив (символи без букв): " + fr.processed);
            System.out.println("Час читання (ms): " + fr.readMs + "; Загальний час (ms): " + fr.totalMs);
        }).thenRunAsync(() -> System.out.println("Завершено обробку файлу: " + filename));
    } else {
        // Аналогічно з executor
    }

    printFutures.add(printFuture);
}
```

Пояснення: Використовується `supplyAsync` для читання, `thenApplyAsync` для обробки (видалення літер за допомогою `\p{L}`). Результати зберігаються в `FileResult`. Окремо асинхронно виводиться інформація про файл.

---

# 4. Об'єднання результатів обробки файлів

[↑Нагору↑](#Загальна-структура-програми)

Після завершення всіх обробок символи з файлів об'єднуються в один список.

Фрагмент коду:

```java
CompletableFuture<List<Character>> merged = CompletableFuture
        .allOf(fileFutures.toArray(new CompletableFuture<?>[0]))
        .thenApply(v ->
                fileFutures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(fr -> fr.processed.stream())
                        .collect(Collectors.toList())
        );
```

Пояснення: `allOf` чекає завершення всіх futures, потім об'єднує списки символів.

---

# 5. Асинхронний запис і вивід об'єднаного масиву

[↑Нагору↑](#Загальна-структура-програми)

Об'єднаний список асинхронно записується в файл і виводиться.

Фрагмент коду:

```java
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
```

Пояснення: Використовується `thenAcceptAsync` для форматування, запису та виводу. Вимірюється час запису.

---

# 6. Моніторинг і чекання завершення частини 1

[↑Нагору↑](#Загальна-структура-програми)

Всі задачі об'єднуються, додається асинхронний лог, і чекається завершення.

Фрагмент коду:

```java
List<CompletableFuture<?>> allPart1Tasks = new ArrayList<>();
allPart1Tasks.addAll(printFutures);
allPart1Tasks.add(writeAndPrintMerged);

CompletableFuture<Void> part1All = CompletableFuture.allOf(allPart1Tasks.toArray(new CompletableFuture<?>[0]));

logger = CompletableFuture.runAsync(() -> System.out.println("\n(Лог) Асинхронні задачі зчитування та обробки запущені..."), executor);

CompletableFuture.allOf(part1All, logger).join();

Instant part1End = Instant.now();
System.out.println("\nЧас завершення всіх асинхронних операцій (частина 1): " +
        Duration.between(part1Start, part1End).toMillis() + " ms\n");
```

Пояснення: `allOf` синхронізує задачі, `runAsync` для логування. `join` блокує до завершення. Вимірюється загальний час частини.

---

# 7. Очікування переходу до частини 2

[↑Нагору↑](#Загальна-структура-програми)

Програма чекає натискання Enter для продовження.

Фрагмент коду:

```java
System.out.println("Натисніть Enter щоб продовжити до Частини 2...");
scanner.nextLine();
```

Пояснення: Це дозволяє користувачу контролювати перехід між частинами.

---

# 8. Частина 2: Генерація послідовності

[↑Нагору↑](#Загальна-структура-програми)

Асинхронно генерується послідовність з 20 випадкових чисел.

Фрагмент коду:

```java
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
```

Пояснення: `supplyAsync` генерує масив з затримкою для демонстрації асинхронності. Час вимірюється в наносекундах.

---

# 9. Обчислення суми добутків

[↑Нагору↑](#Загальна-структура-програми)

Асинхронно обчислюється сума добутків суміжних елементів.

Фрагмент коду:

```java
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
```

Пояснення: `thenApplyAsync` виконує обчислення на основі згенерованої послідовності. Час вимірюється в наносекундах.

---

# 10. Вивід результатів частини 2

[↑Нагору↑](#Загальна-структура-програми)

Асинхронно виводиться послідовність і результат обчислення.

Фрагмент коду:

```java
printSeqAndResult = seqFuture.thenAcceptAsync(sr -> {
    System.out.println("Початкова послідовність (n = " + sr.arr.length + "), час генерації (ms): " + sr.genMs);
    System.out.println(Arrays.toString(sr.arr));
}, executor).thenCompose(v -> compFuture.thenAcceptAsync(cr -> {
    System.out.println("Результат обчислення a1*a2 + a2*a3 + ... + a_{n-1}*a_n = " + cr.sum);
    System.out.println("Час обчислення (ns): " + cr.computeMs);
}, finalExecutor)).thenRunAsync(() -> System.out.println("Завершено обчислення (частина 2)."), finalExecutor);

printSeqAndResult.join();

Instant part2End = Instant.now();
System.out.println("\nЧас виконання усіх асинхронних операцій (частина 2): " +
        Duration.between(part2Start, part2End).toMillis() + " ms");
```

Пояснення: `thenAcceptAsync` і `thenCompose` для ланцюжкового виводу. `thenRunAsync` для фінального повідомлення. `join` чекає завершення.

---
