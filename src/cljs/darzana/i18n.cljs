(ns darzana.i18n
  (:require [net.unit8.tower :as tower]))

(def my-tconfig
  { :dev-mode? true
    :fallback-locale :en
    :dictionary
    { :en
      { :labels
        { :marga "Routing"
          :if-success "if success"
          :if-contains "if contains"
          :call-api "Call API"
          :render "render"
          :redirect "redirect"
          :success "success"
          :error "error"
          :alternative "alternative"
          :convert "Convert"
          :current-workspace "Current workspace"
          :saving "Saving..."
          :saved-successfully "Saved!"
          :failed "Failed"}
        :buttons
        { :save "Save"
          :back "Back" }
        :messages
        { :manage-components "Manage components"
          }
        :titles
        { :list-templates "List of templates"
          :list-apis "List of apis"
          }
        :missing "<Missing translation: [%1$s %2$s %3$s]>"}
      :ja
      { :labels
        { :marga "ルーティング"
          :if-success ""
          :i-contains "このキーワードが"
          :call-api "API呼び出し"
          :render "レンダリング"
          :redirect "リダイレクト"
          :success "成功の場合"
          :error "失敗の場合"
          :alternative "代替案"
          :convert "A/Bテスト コンバージョン"
          :current-workspace "現在のワークスペース"}
        :messages
        { :manage-components "コンポーネントの管理"
          :lead-api-list "登録済みのAPIの一覧です。詳細を見たり、試しに実行したりすることが可能です。"
          :lead-router-list "ルーティング定義の一覧です。masterブランチ以外では実際に編集することができます。"
          :lead-template-list "テンプレートの一覧です。masterブランチ以外では実際に編集することができます。"
          }
        :titles
        { :list-templates "テンプレート一覧"
          :list-apis "API一覧"
          }}}
    })

(defn t [k]
  (tower/t :ja my-tconfig k))

