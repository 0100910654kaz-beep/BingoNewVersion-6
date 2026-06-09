package servlet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class BingoGame implements Serializable {
    private static final long serialVersionUID = 1L;

    private String gameId;                             
    private List<Integer> drawnNumbers;                
    private List<PlayerResult> bingoPlayers;           
    private List<PlayerResult> reachPlayers;           
    private List<String> allPlayers;                   
    private Date expireTime;                           
    private Date lastBingoTime;                        
    private int anonymousCount = 0;                    

    private ConcurrentHashMap<String, List<List<String>>> playerCards = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, List<String>> playerWaitNumbers = new ConcurrentHashMap<>();

    public BingoGame(String gameId, int validDays) {
        this.gameId = gameId;
        this.drawnNumbers = new CopyOnWriteArrayList<>();
        this.bingoPlayers = new CopyOnWriteArrayList<>();
        this.reachPlayers = new CopyOnWriteArrayList<>();
        this.allPlayers = new CopyOnWriteArrayList<>();
        this.lastBingoTime = new Date(); 

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, validDays);
        this.expireTime = cal.getTime();
    }

    public synchronized String generateNextPlayerName() {
        this.anonymousCount++;
        String autoName = "プレイヤー" + this.anonymousCount;
        if (!allPlayers.contains(autoName)) {
            allPlayers.add(autoName);
        }
        return autoName;
    }

    public List<List<String>> getPlayerCard(String name) {
        return playerCards.get(name);
    }

    public void setPlayerCard(String name, List<List<String>> card) {
        playerCards.put(name, card);
        if (!allPlayers.contains(name)) {
            allPlayers.add(name);
        }
    }

    public synchronized void checkAndRegisterStatus(String name, List<List<String>> card) {
        if (drawnNumbers.isEmpty() || card == null || card.isEmpty()) {
            reachPlayers.removeIf(p -> p.getPlayerName().equals(name));
            bingoPlayers.removeIf(p -> p.getPlayerName().equals(name));
            playerWaitNumbers.remove(name);
            return;
        }

        boolean hasBingo = false;
        boolean hasReach = false;
        List<String> minWaitNumbers = null;

        // 横のラインチェック
        for (int r = 0; r < 5; r++) {
            List<String> waitNums = new ArrayList<>();
            for (int c = 0; c < 5; c++) {
                String num = card.get(r).get(c);
                if (!"0".equals(num) && !drawnNumbers.contains(Integer.parseInt(num))) {
                    waitNums.add(num);
                }
            }
            if (waitNums.isEmpty()) hasBingo = true;
            else if (waitNums.size() == 1) {
                hasReach = true;
                if (minWaitNumbers == null || waitNums.size() < minWaitNumbers.size()) {
                    minWaitNumbers = waitNums;
                }
            }
        }

        // 縦のラインチェック
        for (int c = 0; c < 5; c++) {
            List<String> waitNums = new ArrayList<>();
            for (int r = 0; r < 5; r++) {
                String num = card.get(r).get(c);
                if (!"0".equals(num) && !drawnNumbers.contains(Integer.parseInt(num))) {
                    waitNums.add(num);
                }
            }
            if (waitNums.isEmpty()) hasBingo = true;
            else if (waitNums.size() == 1) {
                hasReach = true;
                if (minWaitNumbers == null || waitNums.size() < minWaitNumbers.size()) {
                    minWaitNumbers = waitNums;
                }
            }
        }

        // 斜め（左上から右下）
        {
            List<String> waitNums = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String num = card.get(i).get(i);
                if (!"0".equals(num) && !drawnNumbers.contains(Integer.parseInt(num))) {
                    waitNums.add(num);
                }
            }
            if (waitNums.isEmpty()) hasBingo = true;
            else if (waitNums.size() == 1) {
                hasReach = true;
                if (minWaitNumbers == null || waitNums.size() < minWaitNumbers.size()) {
                    minWaitNumbers = waitNums;
                }
            }
        }

        // 斜め（右上から左下）
        {
            List<String> waitNums = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String num = card.get(i).get(4 - i);
                if (!"0".equals(num) && !drawnNumbers.contains(Integer.parseInt(num))) {
                    waitNums.add(num);
                }
            }
            if (waitNums.isEmpty()) hasBingo = true;
            else if (waitNums.size() == 1) {
                hasReach = true;
                if (minWaitNumbers == null || waitNums.size() < minWaitNumbers.size()) {
                    minWaitNumbers = waitNums;
                }
            }
        }

        // 状態の登録処理
        if (hasBingo) {
            reachPlayers.removeIf(p -> p.getPlayerName().equals(name));
            playerWaitNumbers.remove(name);
            
            boolean alreadyBingo = false;
            for (PlayerResult p : bingoPlayers) {
                if (p.getPlayerName().equals(name)) {
                    alreadyBingo = true;
                    break;
                }
            }
            if (!alreadyBingo) {
                int lastNum = drawnNumbers.isEmpty() ? 0 : drawnNumbers.get(drawnNumbers.size() - 1);
                bingoPlayers.add(0, new PlayerResult(name, new Date(), lastNum));
                this.lastBingoTime = new Date();
            }
        } else if (hasReach) {
            bingoPlayers.removeIf(p -> p.getPlayerName().equals(name));
            if (minWaitNumbers != null) {
                playerWaitNumbers.put(name, minWaitNumbers);
            }
            
            boolean alreadyReach = false;
            for (PlayerResult p : reachPlayers) {
                if (p.getPlayerName().equals(name)) {
                    alreadyReach = true;
                    break;
                }
            }
            if (!alreadyReach) {
                int lastNum = drawnNumbers.isEmpty() ? 0 : drawnNumbers.get(drawnNumbers.size() - 1);
                reachPlayers.add(new PlayerResult(name, new Date(), lastNum));
            }
        } else {
            reachPlayers.removeIf(p -> p.getPlayerName().equals(name));
            playerWaitNumbers.remove(name);
        }
    }

    public synchronized void resetGame() {
        drawnNumbers.clear();
        bingoPlayers.clear();
        reachPlayers.clear();
        playerWaitNumbers.clear();
        playerCards.clear();
        allPlayers.clear();
        anonymousCount = 0;
        lastBingoTime = new Date();
    }

    public synchronized List<String> getWaitNumbers(String name) {
        return playerWaitNumbers.getOrDefault(name, new ArrayList<>());
    }

    public boolean isExpired() { return new Date().after(this.expireTime); }
    
    public boolean isPast2HoursFromLastBingo() {
        if (drawnNumbers.isEmpty() && bingoPlayers.isEmpty()) return false;
        long twoHoursInMilliseconds = 2L * 60 * 60 * 1000;
        long timePassed = new Date().getTime() - lastBingoTime.getTime();
        return timePassed > twoHoursInMilliseconds;
    }

    public String getGameId() { return gameId; }
    public List<Integer> getDrawnNumbers() { return drawnNumbers; }
    public List<PlayerResult> getBingoPlayers() { return bingoPlayers; }
    public List<PlayerResult> getReachPlayers() { return reachPlayers; }
    public int getPlayerCount() { return playerCards.size(); }
    public List<String> getAllPlayers() { return allPlayers; }
}
