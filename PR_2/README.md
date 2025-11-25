# Асинхронна обробка масивів у Java із використанням Callable, Future та ExecutorService

Програма демонструє асинхронну обробку масивів у Java з використанням пулу потоків, `Callable`, `Future`, потокобезпечних колекцій та механізмів моніторингу виконання задач.

Користувач вводить діапазон значень та довжину масиву.
Масив випадкових дробових чисел розбивається на частини, кожна частина обробляється **в окремому потоці**, а обчислені квадрати збираються та виводяться.

---

# Загальна структура програми

1.  [Отримання даних](#1-отримання-даних-про-масив-від-користувача)
2.  [Генерація випадкового масиву](#2-генерація-випадкового-масиву)
3.  [Розбиття масиву](#3-розбиття-масиву-на-чанки)
4.  [Асинхронна обробка](#4-асинхронна-обробка-Callable)
5.  [Моніторинг future](#5-моніторинг-Future-у-режимі-реального-часу)
6.  [Збір результатів](#6-збір-результатів)
7.  [Збереження у copyonwritearrayset](#7-збереження-у-CopyOnWriteArraySet)
8.  [Вимірювання часу](#8-вимірювання-часу-виконання)
9.  [Повний вивід масивів](#9-додатковий-вивід-повної-інформації-про-масиви)

Нижче кожен етап окремо.

---

# 1. Отримання даних про масив від користувача

[↑Нагору↑](#Загальна-структура-програми)

На початку програма запитує у користувача мінімальне і максимальне значення діапазону, а також кількість елементів. 
Фрагмент коду:

```java
System.out.print("Введіть мінімальне значення (double), або Enter для 0.5: ");
String minLine = sc.nextLine().trim();
double min = minLine.isEmpty() ? 0.5 : Double.parseDouble(minLine);

System.out.print("Введіть максимальне значення (double), або Enter для 99.5: ");
String maxLine = sc.nextLine().trim();
double max = maxLine.isEmpty() ? 99.5 : Double.parseDouble(maxLine);

System.out.print("Введіть кількість елементів масиву (ціле від 40 до 60), або Enter для 50: ");
String nLine = sc.nextLine().trim();
int n = nLine.isEmpty() ? 50 : Integer.parseInt(nLine);
```

Якщо ввести менше / більше можливих значень - примусови наближення до можливих значень за завданням:

Фрагмент коду:

```java
if (min < 0.5) { System.out.println("min < 0.5 — приведено до 0.5"); min = 0.5; }
if (max > 99.5) { System.out.println("max > 99.5 — приведено до 99.5"); max = 99.5; }
if (min > max) {
System.out.println("min > max — міняю місцями.");
double t = min; min = max; max = t;
```

та

```java
int n = nLine.isEmpty() ? 50 : Integer.parseInt(nLine);
if (n < 40) { System.out.println("Кількість менше 40 — встановлено 40"); n = 40; }
if (n > 60) { System.out.println("Кількість більше 60 — встановлено 60"); n = 60; }
```

[↑Нагору↑](#-Скріншот-№1)



---

# 2. Генерація випадкового масиву

[↑Нагору↑](#Загальна-структура-програми)

Після введення параметрів створюється масив випадкових дробових чисел у вказаному діапазоні.

Фрагмент коду:

```java
double[] array = new double[n];
Random rnd = new Random();
for (int i = 0; i < n; i++) {
    array[i] = min + rnd.nextDouble() * (max - min);
}
```

---

# 3. Розбиття масиву на чанки

[↑Нагору↑](#Загальна-структура-програми)

Масив ділиться на частини розміром по 10 елементів. Кожна частина буде оброблятися окремим Callable у своєму потоці.
Наприклад:
n = 50, chunk = 10 → буде 5 частин.
Якщо n = 53 → буде 6 частин.
*n - розмір масиву.

Фрагмент коду:

```java
final int chunkSize = 10;
final int chunks = (n + chunkSize - 1) / chunkSize;

for (int i = 0; i < chunks; i++) {
    int from = i * chunkSize;
    int to = Math.min(from + chunkSize, n);
    double[] sub = Arrays.copyOfRange(array, from, to);
    SquareArrayTask task = new SquareArrayTask(sub, perElementMillis);
    Future<double[]> f = executor.submit(task);
    futures.add(f);
}
```

Пояснення: тут нарізається загальний масив на частини. Кожна частина працює незалежно в окремому потоці.

---

# 4. Асинхронна обробка Callable

[↑Нагору↑](#Загальна-структура-програми)

Окремий клас SquareArrayTask, який отримує частину масиву і повертає масив квадратів значень початкового масиву.

Фрагмент коду:

```java
static class SquareArrayTask implements Callable<double[]> {
    private final double[] input;
    private final long perElementMillis;

    public SquareArrayTask(double[] input, long perElementMillis) {
        this.input = input;
        this.perElementMillis = perElementMillis;
    }

    @Override
    public double[] call() throws Exception {
        double[] out = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Task was interrupted");
            }
            double v = input[i];
            out[i] = v * v;

            if (perElementMillis > 0) {
                Thread.sleep(perElementMillis);
            }
        }
        return out;
    }
}
```

Пояснення: Callable рахує квадрат кожного числа у своїй частині масиву. Я додав невелику затримку, щоб було видно зміну статусів isDone.

```
long perElementMillis = 30;
```

Затримка для кожного елемента масиву становить 30 мілісекунд. ТОБТО:

якщо у цій частині масиву 10 елементів → задача спатиме 10 × 30 = 300 мс

Загальна тривалість роботи одного чанку = (кількість елементів у цьому чанку) × 30 мс. (далі буде показаноо результат зміни затримок у коді)


---

# 5. Моніторинг Future у режимі реального часу

[↑Нагору↑](#Загальна-структура-програми)

Поки задачі виконуються - перевіряються їх статуси.

Фрагмент коду:

```java
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
        Thread.sleep(200);
    }
}
```

Пояснення: я вивожу інформацію про стан кожної задачі. Якщо done=false – задача ще виконується. Якщо done=true – задача завершена. Завжди cancelled=false тому-що він ніде і не викликається. Також додана затримка у 200 мілісекунд (насправді для запобігання флуду)

---

# 6. Збір результатів

[↑Нагору↑](#Загальна-структура-програми)

Коли всі задачі завершені, отримується значення квадратів чисел масиву через future.get().

Фрагмент коду:

```java
for (int i = 0; i < futures.size(); i++) {
    Future<double[]> f = futures.get(i);
    if (f.isCancelled()) {
        continue;
    }
    try {
        double[] res = f.get();
        for (double v : res) {
            resultSet.add(v);
            totalCollected.incrementAndGet();
        }
    } catch (ExecutionException | InterruptedException e) {
        ...
    }
}
```

Пояснення: тут отримуються результати всіх потоків та додаю їх у спільну колекцію. Далі буде пояснення НЕгарантованого порядку чисел у кінцевому масиві.

---

# 7. Збереження у CopyOnWriteArraySet

[↑Нагору↑](#Загальна-структура-програми)

Результати записуються у потокобезпечну колекцію.

Фрагмент коду:

```java
CopyOnWriteArraySet<Double> resultSet = new CopyOnWriteArraySet<>();
```

Пояснення: ця структура дозволяє безпечно додавати значення з різних потоків і автоматично уникає дублювання. АЛЕ:

У Java:

    - HashSet
    - CopyOnWriteArraySet
    - ConcurrentHashMap.newKeySet()

усі вони НЕ гарантують порядок вставки.

CopyOnWriteArraySet всередині використовує CopyOnWriteArrayList, але тільки як контейнер, при цьому вона:

    - може змінювати порядок елементів під час вставки (через внутрішні операції),
    - НЕ гарантує, що елементи будуть у тому ж порядку, в якому потоки додавали значення.
---

# 8. Вимірювання часу виконання

[↑Нагору↑](#Загальна-структура-програми)

Просто фіксуться час початку та завершення обробки.

Фрагмент коду:

```java
long startTime = System.nanoTime();
...
long endTime = System.nanoTime();
long elapsedMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

System.out.printf("Час роботи програми: %d ms%n", elapsedMs);
```

---

# 9. Додатковий вивід повної інформації про масиви

[↑Нагору↑](#Загальна-структура-програми)

Програма пропонує показати повністю:

    - початковий масив
    - масив квадратів

Фрагмент коду:

```java
System.out.print("\nВивести повну інформацію про масиви? (Y/N): ");
String ans = sc.nextLine().trim().toUpperCase();

if (ans.equals("Y")) {
    StringBuilder sbInit = new StringBuilder("[ ");
    for (int i = 0; i < array.length; i++) {
        sbInit.append(String.format("%.6f", array[i]));
        if (i < array.length - 1) sbInit.append(" | ");
    }
    sbInit.append(" ]");

    StringBuilder sbFinal = new StringBuilder("[ ");
    for (double v : array) {
        sbFinal.append(String.format("%.6f", v * v));
        sbFinal.append(" | ");
    }
    sbFinal.setLength(sbFinal.length() - 3);
    sbFinal.append(" ]");

    System.out.println("\n=== Початковий масив ===");
    System.out.println(sbInit);

    System.out.println("\n=== Масив квадратів (кінцевий) ===");
    System.out.println(sbFinal);
}
```

Також, при такому виводі вже НЕМАЄ багатопотоку. Тому і гарандовано, що 45-те число другого масиву відповідатиме квадрату 45-го числа першого масиву. 

---

Продубльовані скріншоту зі звіту:

# Скріншот №1

<img width="987" height="414" alt="image" src="https://github.com/user-attachments/assets/dc37e182-0191-4560-8426-be644408ab74" />

Рис1. Стандартна робота програми

# Скріншот №2

<img width="941" height="132" alt="image" src="https://github.com/user-attachments/assets/5a3c90f9-9e03-4043-99de-d1c2c04be1a5" />

Рис2. Вивід повної інформації про обидва масиви

# Скріншот №3

<img width="941" height="558" alt="image" src="https://github.com/user-attachments/assets/896528df-493b-454c-9e2f-76fd585427a6" />

Рис3. Нульові затримки при виконанні програми



