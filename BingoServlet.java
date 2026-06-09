package servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@WebServlet("/BingoServlet")
public class BingoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    // 全部屋のゲームデータを一元管理する共通メモリ空間
    private static final ConcurrentHashMap<String, BingoGame> games = new ConcurrentHashMap<>();

    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        processRequest(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
        HttpSession session = request.getSession(true);

        String action = request.getParameter("action");

        // =================================================================
        // 👑 1. 司会者専用処理ブロック（プレイヤー用チェックに割り込まれないよう最優先処理）
        // =================================================================
        
        // 【新規部屋作成】
        if ("createRoom".equals(action)) {
            String newGameId;
            synchronized (games) {
                do {
                    // 0000〜9999の4桁の部屋IDをランダム自動生成
                    newGameId = String.format("%04d", (int)(Math.random() * 10000));
                } while (games.containsKey(newGameId)); // 重複があれば再生成
                
                // 有効期限1日の部屋オブジェクトを生成して共通マップに登録
                BingoGame newGame = new BingoGame(newGameId, 1);
                games.put(newGameId, newGame);
            }
            // セッションに司会者自身の現在のゲームIDを記憶させておく
            session.setAttribute("myCurrentGameId", newGameId);
            
            request.setAttribute("game", games.get(newGameId));
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return; // 確実にここで処理を終了させ、下のプレイヤーチェックへ落とさない
        }

        // 【司会者：数字の抽選】
        if ("draw".equals(action)) {
            String adminGameId = request.getParameter("gameId");
            BingoGame adminGame = games.get(adminGameId);
            if (adminGame != null) {
                List<Integer> drawn = adminGame.getDrawnNumbers();
                if (drawn.size() < 75) {
                    int nextNum;
                    do {
                        nextNum = (int)(Math.random() * 75) + 1;
                    } while (drawn.contains(nextNum));
                    drawn.add(nextNum);
                    
                    // 数字を引いたタイミングで、全プレイヤーのリーチ・ビンゴ状態を裏で自動更新
                    for (String pName : adminGame.getAllPlayers()) {
                        List<List<String>> pCard = adminGame.getPlayerCard(pName);
                        if (pCard != null && !pCard.isEmpty()) {
                            adminGame.checkAndRegisterStatus(pName, pCard);
                        }
                    }
                }
                request.setAttribute("game", adminGame);
            }
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 【司会者：ゲームリセット】
        if ("reset".equals(action)) {
            String adminGameId = request.getParameter("gameId");
            BingoGame adminGame = games.get(adminGameId);
            if (adminGame != null) {
                adminGame.resetGame();
                request.setAttribute("game", adminGame);
            }
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }

        // 【司会者：管理画面の再表示・リフレッシュ】
        if ("adminView".equals(action)) {
            String adminGameId = request.getParameter("gameId");
            BingoGame adminGame = games.get(adminGameId);
            if (adminGame != null) {
                request.setAttribute("game", adminGame);
            }
            request.getRequestDispatcher("admin.jsp").forward(request, response);
            return;
        }


        // =================================================================
        // 👤 2. 一般プレイヤー専用処理ブロック
        // =================================================================
        
        // リクエスト、またはセッションからターゲットとなる部屋ID（gameId）を取得
        String targetGameId = request.getParameter("gameId");
        if (targetGameId == null || targetGameId.isEmpty()) {
            targetGameId = (String) session.getAttribute("myCurrentGameId");
        }

        // 部屋IDが有効かつ、現在サーバー上に存在するか厳格にチェック
        if (targetGameId != null && targetGameId.length() == 4 && games.containsKey(targetGameId)) {
            session.setAttribute("myCurrentGameId", targetGameId);
        } else {
            // 部屋が見つからない、または新規参加アクションでもない場合はエラーとしてトップに返す
            if (!"join".equals(action)) {
                request.setAttribute("error", "⚠️ 部屋の指定が正しくないか、有効期限が切れています。");
                request.getRequestDispatcher("index.jsp").forward(request, response);
                return;
            }
        }

        BingoGame currentGame = games.get(targetGameId);
        if (currentGame == null) {
            session.removeAttribute("card");
            request.setAttribute("error", "⚠️ お探しのビンゴ部屋が見つかりませんでした。");
            request.getRequestDispatcher("index.jsp").forward(request, response);
            return;
        }

        // ⚡ 司会者がゲームを完全リセット（数字が空）したら、古いセッション情報を完全消去
        if (currentGame.getDrawnNumbers().isEmpty()) {
            session.removeAttribute("card");
            session.removeAttribute("myConfirmedName");
        }

        String confirmedName = (String) session.getAttribute("myConfirmedName");

        // 🚪【名前の全自動割り振りロジック】
        // はじめての参加（join）、またはセッションに名前がない場合、サーバー側で「プレイヤー1」などの名前を確定
        if ("join".equals(action) || confirmedName == null || confirmedName.isEmpty()) {
            if (confirmedName == null || confirmedName.isEmpty()) {
                synchronized (currentGame) {
                    // BingoGame側のカウンタを利用して、重複のない安全な自動プレイヤー名を生成
                    String uniqueName = currentGame.generateNextPlayerName();
                    // 競合防止のため、仮の空リストで初期領域を確保
                    currentGame.setPlayerCard(uniqueName, new ArrayList<>());
                    confirmedName = uniqueName;
                }
                session.setAttribute("myConfirmedName", confirmedName);
                session.removeAttribute("card"); // 新規カード生成を促すためクリア
            }
        }

        // セッションからビンゴカードを取得
        @SuppressWarnings("unchecked")
        List<List<String>> card = (List<List<String>>) session.getAttribute("card");
        
        // 🔄 Render瞬断・セッション切れ対策：
        // セッションからカードが消えていても、サーバーの共通メモリに残っていれば自動回収して完全復旧
        if (card == null && confirmedName != null && !confirmedName.isEmpty()) {
            card = currentGame.getPlayerCard(confirmedName);
            // join直後に確保した「中身が空のリスト」だった場合は、新規作成させるためにnull扱いにする
            if (card != null && card.isEmpty()) {
                card = null;
            }
            if (card != null) {
                session.setAttribute("card", card);
            }
        }
        
        // カードが存在する場合、サーバー側の共通メモリへもしっかり同期保存
        if (card != null && confirmedName != null && !confirmedName.isEmpty()) {
            currentGame.setPlayerCard(confirmedName, card);
            // 現在の出目状況に合わせてリーチ・ビンゴ状態の判定を随時同期更新
            currentGame.checkAndRegisterStatus(confirmedName, card);
        }

        // JSP側へ渡す属性値をしっかりとセット
        request.setAttribute("game", currentGame);
        request.setAttribute("confirmedPlayerName", confirmedName);
        request.setAttribute("gameId", targetGameId);

        request.getRequestDispatcher("index.jsp").forward(request, response);
    }
}
