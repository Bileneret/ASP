import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Semaphore;

/**
 * Симуляція постачання товарів
 *
 * Вимоги:
 * - 1 сек реального = 5 хв симуляції (×300 секунд)
 * - покупець купує від 1 до 3 товарів
 * - постачальник привозить від 3 до 7 товарів
 * - покупець приходить кожні 5..10 хв симуляції (1..2 с реального)
 * - постачальник кожні 15..20 хв симуляції (3..4 с реального)
 * - робочі години 08:00–20:00, обід постачальника 12:00–13:00
 * - Semaphore + Runnable + обробка помилок + коментарі
 */
public class Main {

    /** 1 реальна секунда = 5 симуляц. хвилин = 300 симуляц. секунд
    private static final int SIM_SECONDS_PER_REAL_SECOND = 300;*/

    // 1 реальна секунда = 20 симуляц. хвилин = 300 симуляц. секунд
    private static final int SIM_SECONDS_PER_REAL_SECOND = 1200;

    public static void main(String[] args) throws InterruptedException {
        Scanner scanner = new Scanner(System.in);
        Random random = new Random();

        System.out.print("Скільки годин симуляції виконати? (наприклад, 24): ");
        int hours = 0;
        while (hours < 1) {
            try {
                hours = Integer.parseInt(scanner.nextLine().trim());
            } catch (Exception e) {
                System.out.print("Введіть правильне число ≥ 1: ");
            }
        }

        long simulatedSeconds = hours * 3600L;
        long realDurationMillis = simulatedSeconds * 1000L / SIM_SECONDS_PER_REAL_SECOND;

        System.out.printf("%nСИМУЛЯЦІЯ ЗАПУЩЕНА (1 с реального = 20 хв симуляції)%n");
        System.out.printf("%d годин симуляції ≈ %.2f хв реального часу%n%n",
                hours, realDurationMillis / 60000.0);

        // створюємо годинник і склад
        Clock clock = new Clock(SIM_SECONDS_PER_REAL_SECOND, LocalTime.of(8, 0)); // початок симуляції 08:00
        Warehouse warehouse = new Warehouse(100, clock);

        // створюємо потоки
        Thread supplier = new Thread(new Supplier(warehouse, clock, random), "Постачальник");
        Thread customer = new Thread(new Customer(warehouse, clock, random), "Покупець");

        Logger.print(clock, "Старт симуляції");
        Logger.print(clock, String.format("Місткість складу: %d | Старт: %d товарів", 100, warehouse.getProductsCount()));
        Logger.print(clock, "Робочі години: 08:00–20:00 | Обід постачальника: 12:00–13:00");
        Logger.printSeparator();

        // стартуємо
        supplier.start();
        customer.start();

        // чекаємо завершення симуляції
        try {
            Thread.sleep(realDurationMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Logger.printSeparator();
        Logger.print(clock, "ЗАВЕРШЕННЯ СИМУЛЯЦІЇ — завершуємо потоки");

        supplier.interrupt();
        customer.interrupt();

        Logger.print(clock, "Стан потоків перед join(): "
                + supplier.getName() + "=" + supplier.getState() + ", "
                + customer.getName() + "=" + customer.getState());

        supplier.join(3000);
        customer.join(3000);

        Logger.print(clock, "Симуляція завершена");
        Logger.print(clock, String.format("На складі: %,d із %,d товарів", warehouse.getProductsCount(), warehouse.getCapacity()));
        Logger.print(clock, "Дякую за тестування!");

        scanner.close();
    }
}


/** ========================= Clock ========================= **/
class Clock {
    private final long startMillis;
    private final int simSecondsPerRealSecond;
    private final LocalTime startTimeOfDay;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private LocalTime lastTime = null;

    private boolean curfewStartedAnnounced = false;
    private boolean curfewEndedAnnounced = false;
    private long lastDayChecked = -1;

    public Clock(int simSecondsPerRealSecond, LocalTime startTimeOfDay) {
        this.startMillis = System.currentTimeMillis();
        this.simSecondsPerRealSecond = simSecondsPerRealSecond;
        this.startTimeOfDay = startTimeOfDay;
    }

    public synchronized long simulatedSecondsSinceStart() {
        long elapsedMillis = System.currentTimeMillis() - startMillis;
        return elapsedMillis * simSecondsPerRealSecond / 1000L;
    }

    /** Формує рядок та оголошує комендантську годину */
    public synchronized String formatted() {

        long totalSimSec = simulatedSecondsSinceStart();
        long day = totalSimSec / 86400L;               // 24 * 60 * 60
        long secondsOfDay = totalSimSec % 86400L;
        LocalTime t = startTimeOfDay.plusSeconds(secondsOfDay);

        /** =================== Перехід на новий день =================== */
        if (day != lastDayChecked) {
            curfewStartedAnnounced = false;
            curfewEndedAnnounced = false;
            lastDayChecked = day;
        }

        /** =================== Детектор переходу часу =================== */
        if (lastTime != null) {

            /* === Настала комендантська година (00:00) === */
            if (!curfewStartedAnnounced &&
                    lastTime.getHour() != 0 &&
                    t.getHour() == 0 &&
                    t.getMinute() == 0) {

                Logger.print(this, "Настала комендантська година");
                curfewStartedAnnounced = true;
            }

            /* === Комендантська година закінчилася (05:00) === */
            if (!curfewEndedAnnounced &&
                    lastTime.getHour() < 5 &&
                    t.getHour() == 5 &&
                    t.getMinute() == 0) {

                Logger.print(this, "Комендантська година закінчилася");
                curfewEndedAnnounced = true;
            }
        }

        lastTime = t;

        return String.format("Day %d %s", day, t.format(TIME_FMT));
    }

    public synchronized Clock.SimTime getSimTime() {
        long totalSimSec = simulatedSecondsSinceStart();
        long day = totalSimSec / 86400L;
        long secondsOfDay = totalSimSec % 86400L;
        LocalTime t = startTimeOfDay.plusSeconds(secondsOfDay);
        return new Clock.SimTime(day, t);
    }

    public synchronized boolean isShopOpen() {
        LocalTime t = getSimTime().time;
        return !t.isBefore(LocalTime.of(8, 0)) && t.isBefore(LocalTime.of(20, 0));
    }

    public synchronized boolean isSupplierOnLunch() {
        LocalTime t = getSimTime().time;
        return !t.isBefore(LocalTime.of(12, 0)) && t.isBefore(LocalTime.of(13, 0));
    }

    public synchronized boolean isSupplierWorkingHours() {
        LocalTime t = getSimTime().time;
        return !t.isBefore(LocalTime.of(7, 0)) && t.isBefore(LocalTime.of(19, 0));
    }

    public synchronized boolean isCurfew() {
        LocalTime t = getSimTime().time;
        return !t.isBefore(LocalTime.of(0, 0)) && t.isBefore(LocalTime.of(5, 0));
    }

    public static class SimTime {
        public final long day;
        public final LocalTime time;

        public SimTime(long day, LocalTime time) {
            this.day = day;
            this.time = time;
        }
    }
}



/** ========================= Logger ========================= **/
class Logger {
    public static synchronized void print(Clock clock, String message) {
        System.out.printf("[%s] %s%n", clock.formatted(), message);
    }

    public static synchronized void printSeparator() {
        System.out.println("-".repeat(70));
    }
}


/** ========================= Warehouse ========================= **/
class Warehouse {
    private final Semaphore semaphore;
    private int products = 5;
    private final int capacity;
    private final Clock clock;

    public Warehouse(int capacity, Clock clock) {
        this.capacity = capacity;
        this.clock = clock;
        this.semaphore = new Semaphore(capacity, true);

        semaphore.drainPermits();
        semaphore.release(capacity - products);

        Logger.print(clock, String.format("Склад відкрито → старт: %d товарів (місткість %,d)", products, capacity));
    }

    public synchronized void addProducts(int amount) throws InterruptedException {
        int free = capacity - products;
        int added = Math.min(amount, free);

        if (added > 0) {
            for (int i = 0; i < added; i++) semaphore.acquire();
            products += added;
            Logger.print(clock, String.format("Постачальник привіз %,d (всього %,d/%,d)", added, products, capacity));
        } else {
            Logger.print(clock, "Постачальник прийшов → склад повністю заповнений");
        }
    }

    public synchronized void removeProducts(int desired) {
        int taken = Math.min(desired, products);

        if (taken == 0) {
            Logger.print(clock, "Покупець прийшов → товарів немає");
            return;
        }

        for (int i = 0; i < taken; i++) semaphore.release();

        products -= taken;
        Logger.print(clock, String.format("Покупець купив %,d → залишилось %,d/%,d", taken, products, capacity));
    }

    public synchronized int getProductsCount() {
        return products;
    }

    public int getCapacity() {
        return capacity;
    }
}


/** ========================= Supplier ========================= **/
class Supplier implements Runnable {
    private final Warehouse warehouse;
    private final Clock clock;
    private final Random rand;

    public Supplier(Warehouse w, Clock c, Random r) {
        warehouse = w;
        clock = c;
        rand = r;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {

                // ком. година, просто пропуск
                if (clock.isCurfew()) {
                    continue;
                }

                if (!clock.isSupplierWorkingHours()) {
                    Logger.print(clock, "Постачальник не працює → неробочий час");
                    Thread.sleep(2000);
                    continue;
                }

                if (clock.isSupplierOnLunch()) {
                    Logger.print(clock, "Постачальник на обіді");
                    Thread.sleep((1500 + rand.nextInt(1001)));
                    continue;
                }

                long realSleep = (3 + rand.nextInt(2)) * 1000L;
                Thread.sleep(realSleep);

                int amount = 3 + rand.nextInt(5); // 3..7
                warehouse.addProducts(amount);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            Logger.print(clock, "Потік Постачальник завершив роботу");
        }
    }
}


/** ========================= Customer ========================= **/
class Customer implements Runnable {
    private final Warehouse warehouse;
    private final Clock clock;
    private final Random rand;

    public Customer(Warehouse w, Clock c, Random r) {
        warehouse = w;
        clock = c;
        rand = r;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {

                long realSleep = (1 + rand.nextInt(2)) * 1000L;
                Thread.sleep(realSleep);

                // ком. година, просто пропуск
                if (clock.isCurfew()) {
                    continue;
                }

                if (!clock.isShopOpen()) {
                    Logger.print(clock, "Покупець прийшов → магазин зачинений");
                    continue;
                }

                int want = 1 + rand.nextInt(3);
                warehouse.removeProducts(want);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            Logger.print(clock, "Потік Покупець завершив роботу");
        }
    }
}
