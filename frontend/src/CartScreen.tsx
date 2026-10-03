import { useState } from 'react'
import { Alert, Button, Empty, InputNumber, Space, Table, Tag, Tooltip, Typography } from 'antd'
import { DeleteOutlined, DownloadOutlined, DownOutlined, EyeOutlined, UpOutlined } from '@ant-design/icons'
import { exportOrder } from './api/doorConfigurations'
import type { CartDetailRow, CartItem } from './cart'
import { totalCartQuantity } from './cart'
import { formatMoney, formatMoneyWithCurrency } from './format'
import { serviceByKey } from './services'

interface CartScreenProps {
  items: CartItem[]
  onUpdateQuantity: (id: string, quantity: number) => void
  onRemove: (id: string) => void
  onEdit: (item: CartItem) => void
  onGoToConfigurator: () => void
}

// В приложении сейчас единственный вид изделия — межкомнатные двери (сервис «Эмаль и шпон»); статус-тег
// показывает фиксированный текст для всех позиций, без нового поля в данных (см. design.md, Non-Goals).
const ITEM_STATUS_LABEL = 'МЕЖКОМНАТНАЯ ДВЕРЬ'
// Тег сервиса — текст берётся по сервису позиции (см. services.ts, cartTag; у старых позиций без serviceKey —
// «Эмаль и шпон»). Название прайс-листа в корзине не выводится. Показывается перед тегом ITEM_STATUS_LABEL,
// но после наименования модели (см. правку пользователя).

// Уникальный яркий цвет на каждый тег атрибута конфигурации (см. правку пользователя, order-cart-ui, «Теги
// атрибутов конфигурации») — отличается и от зелёного SERVICE_TAG_LABEL, и от синего ITEM_STATUS_LABEL, и
// друг от друга; значения — предустановленные яркие цвета antd Tag.
const ATTRIBUTE_TAG_COLORS: Record<string, string> = {
  'РЕВЕРС': 'red',
  'ОСТЕКЛЕНИЕ': 'cyan',
  'ЧЕТВЕРТЬ': 'orange',
  'ТОЛЩИНА 59': 'gold',
  'ЗЕРКАЛО': 'purple',
  'ДВУСТОРОННЯЯ': 'magenta',
}

// priceApplicable=false (см. change show-mirror-glazing-in-order-detail-rows, правку пользователя) — ячейка
// остаётся пустой, а не «—»: прочерк означает именно «цена не найдена» у применимого компонента, лишние
// прочерки у строк вроде исполнения зеркала/вида остекления только мешают восприятию.
function money(value: number | null, priceApplicable: boolean): string {
  if (!priceApplicable) {
    return ''
  }
  return value !== null ? formatMoney(value) : '—'
}

// Вложенная таблица детализации одной позиции по компонентам (см. order-cart-ui, «Разворачиваемая
// детализация позиции корзины») — те же 9 колонок, что и в макете (Figma, «Шапка детализации»): Элемент,
// Наименование, Размеры, Цвет, Кол-во, Цена дилер, Цена клиенту, Сумма дилер, Сумма клиенту.
function DetailTable({ rows }: { rows: CartDetailRow[] }) {
  return (
    <Table<CartDetailRow>
      size="small"
      bordered
      pagination={false}
      dataSource={rows}
      rowKey={(row, index) => `${row.element}-${row.name}-${index}`}
      columns={[
        { title: 'Элемент', dataIndex: 'element' },
        { title: 'Наименование', dataIndex: 'name', width: 140 },
        {
          title: 'Размеры',
          dataIndex: 'size',
          render: (value: string | null, record) => (record.priceApplicable ? (value ?? '—') : ''),
        },
        {
          title: 'Цвет',
          dataIndex: 'colour',
          render: (value: string | null, record) => (record.priceApplicable ? (value ?? '—') : ''),
        },
        { title: 'Кол-во', dataIndex: 'quantity' },
        { title: 'Цена дилер', dataIndex: 'dealerPrice', render: (value: number | null, record) => money(value, record.priceApplicable) },
        { title: 'Цена клиенту', dataIndex: 'retailPrice', render: (value: number | null, record) => money(value, record.priceApplicable) },
        { title: 'Сумма дилер', dataIndex: 'dealerSum', render: (value: number | null, record) => money(value, record.priceApplicable) },
        { title: 'Сумма клиенту', dataIndex: 'retailSum', render: (value: number | null, record) => money(value, record.priceApplicable) },
      ]}
      summary={(data) => {
        const dealerTotal = data.reduce((sum, row) => sum + (row.dealerSum ?? 0), 0)
        const retailTotal = data.reduce((sum, row) => sum + (row.retailSum ?? 0), 0)
        return (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0} colSpan={7}>
              <Typography.Text strong>Итого</Typography.Text>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={1}>
              <Typography.Text strong>{formatMoneyWithCurrency(dealerTotal)}</Typography.Text>
            </Table.Summary.Cell>
            <Table.Summary.Cell index={2}>
              <Typography.Text strong>{formatMoneyWithCurrency(retailTotal)}</Typography.Text>
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )
      }}
    />
  )
}

// Полоса «Детализация конфигурации» под родительской строкой — на всю ширину таблицы, всегда видна (см.
// правку пользователя и макет, Figma «Подзаголовок» перед «Детализация конфигурации»), сама является
// элементом разворота: клик по ней (или по шеврону) показывает/скрывает вложенную DetailTable под собой.
// Реализовано через antd `Table.expandable.expandedRowRender`, но с `expandedRowKeys`, всегда равным всем
// id позиций, и скрытой стандартной стрелкой (`showExpandColumn: false`) — то есть «развёрнутое содержимое»
// строки отображается для каждой строки всегда, а уже само это содержимое решает, показывать ли таблицу.
function ConfigurationDetailSection({ item }: { item: CartItem }) {
  const [expanded, setExpanded] = useState(false)
  return (
    <div>
      <div
        onClick={() => setExpanded((value) => !value)}
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          cursor: 'pointer',
          padding: '4px 0',
        }}
      >
        <Typography.Text strong>Детализация конфигурации</Typography.Text>
        {expanded ? <UpOutlined /> : <DownOutlined />}
      </div>
      {expanded &&
        (item.detailRows && item.detailRows.length > 0 ? (
          <DetailTable rows={item.detailRows} />
        ) : (
          // Позиция добавлена в корзину до того, как появилось поле detailRows (старая запись в
          // localStorage) — вместо пустой таблицы показываем понятное пояснение, а не молча пустой блок.
          <Alert
            style={{ marginTop: 8 }}
            type="warning"
            showIcon
            message="Детализация недоступна для этой позиции"
            description="Похоже, позиция была добавлена в корзину до появления детализации по компонентам. Удалите её и добавьте эту конфигурацию в корзину заново."
          />
        ))}
    </div>
  )
}

// Экран «Корзина заказа» (см. change add-order-cart-screen, order-cart-ui) — таблица добавленных с экрана
// конфигуратора позиций, под каждой строкой — всегда видимая на всю ширину таблицы полоса «Детализация
// конфигурации» с шевроном (см. ConfigurationDetailSection), раскрывающая вложенную таблицу по компонентам;
// отдельно от неё — действие «Посмотреть», открывающее конфигурацию в конфигураторе (см. «Кнопка
// «Посмотреть» открывает конфигурацию в конфигураторе»). Корзина хранится и мутируется в App.tsx
// (localStorage, см. cart.ts) — этот компонент только отображает переданные `items` и вызывает
// пропы-колбэки, сам ничего не сохраняет.
function CartScreen({ items, onUpdateQuantity, onRemove, onEdit, onGoToConfigurator }: CartScreenProps) {
  const [exportLoading, setExportLoading] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

  function handleExportOrder() {
    setExportLoading(true)
    setExportError(null)
    exportOrder(
      items.map((item) => ({
        displayName: item.displayName,
        quantity: item.quantity,
        specification: item.exportRequest,
        attributeTags: item.attributeTags ?? [],
      })),
    )
      .then(({ blob, filename }) => {
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = filename
        link.click()
        URL.revokeObjectURL(url)
      })
      .catch((error: unknown) => {
        setExportError(error instanceof Error ? error.message : 'Не удалось сформировать файл заказа')
      })
      .finally(() => {
        setExportLoading(false)
      })
  }

  const header = (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
      <div>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Корзина заказа
        </Typography.Title>
        <Typography.Text type="secondary">
          Проверьте конфигурации, укажите количество и выгрузите готовый заказ
        </Typography.Text>
      </div>
      <Button onClick={onGoToConfigurator}>Добавить новую конфигурацию</Button>
    </div>
  )

  if (items.length === 0) {
    return (
      <div className="app-main">
        {header}
        <Empty description="В корзине пока нет ни одной конфигурации">
          <Button type="primary" onClick={onGoToConfigurator}>
            Перейти к конфигуратору
          </Button>
        </Empty>
      </div>
    )
  }

  const totalItems = totalCartQuantity(items)
  const orderTotal = items.reduce((sum, item) => sum + item.pricingSnapshot.totalRetailPrice * item.quantity, 0)
  const orderDealerTotal = items.reduce((sum, item) => sum + item.pricingSnapshot.totalDealerPrice * item.quantity, 0)

  return (
    <div className="app-main">
      {header}
      <Table<CartItem>
        rowKey="id"
        bordered
        pagination={false}
        dataSource={items}
        columns={[
          {
            title: '№',
            width: 48,
            render: (_, __, index) => index + 1,
          },
          {
            title: 'Конфигурация',
            width: 300,
            render: (_, item) => (
              <Space direction="vertical" size={4}>
                <Typography.Text>{item.displayName}</Typography.Text>
                <Space size={4} wrap>
                  <Tag color="green">{serviceByKey(item.serviceKey).cartTag}</Tag>
                  <Tag color="blue">{ITEM_STATUS_LABEL}</Tag>
                  {(item.attributeTags ?? []).map((tag) => (
                    <Tag key={tag} color={ATTRIBUTE_TAG_COLORS[tag] ?? 'default'}>
                      {tag}
                    </Tag>
                  ))}
                </Space>
              </Space>
            ),
          },
          {
            title: 'Параметры',
            dataIndex: 'dimensionsLabel',
          },
          {
            title: 'Цена за ед.',
            render: (_, item) => formatMoneyWithCurrency(item.pricingSnapshot.totalRetailPrice),
          },
          {
            title: 'Количество',
            render: (_, item) => (
              <InputNumber
                min={1}
                value={item.quantity}
                onChange={(value) => onUpdateQuantity(item.id, value ?? 1)}
              />
            ),
          },
          {
            title: 'Сумма',
            render: (_, item) => formatMoneyWithCurrency(item.pricingSnapshot.totalRetailPrice * item.quantity),
          },
          {
            title: 'Действия',
            render: (_, item) => (
              <Space size={8}>
                {/* Открывает эту же конфигурацию обратно в конфигураторе «Эмаль и шпон» — не разворачивает
                    детализацию (для этого — полоса «Детализация конфигурации» под строкой, см.
                    ConfigurationDetailSection). */}
                {/* Цвет и обводка — тот же синий, что и у кнопки «Выгрузить заказ» (type="primary",
                    #1677ff), чтобы обе кнопки-иконки визуально совпадали с остальными акцентными
                    элементами экрана (см. правку пользователя). */}
                <Tooltip title="Посмотреть">
                  <Button
                    icon={<EyeOutlined />}
                    style={{ color: '#1677ff', borderColor: '#1677ff' }}
                    onClick={() => onEdit(item)}
                  />
                </Tooltip>
                {/* danger даёт красную обводку и красную иконку «из коробки» (antd Button, type="default"
                    по умолчанию) — отдельный style не нужен. */}
                <Tooltip title="Удалить">
                  <Button danger icon={<DeleteOutlined />} onClick={() => onRemove(item.id)} />
                </Tooltip>
              </Space>
            ),
          },
        ]}
        expandable={{
          expandedRowRender: (item) => <ConfigurationDetailSection item={item} />,
          expandedRowKeys: items.map((item) => item.id),
          showExpandColumn: false,
        }}
      />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', margin: '16px 0' }}>
        <Typography.Text type="secondary">
          {items.length} {items.length === 1 ? 'конфигурация' : 'конфигурации'} · {totalItems}{' '}
          {totalItems === 1 ? 'изделие' : 'изделия'}
        </Typography.Text>
        <Space align="baseline" size={16}>
          <Typography.Text strong>Итого по заказу</Typography.Text>
          <Typography.Title level={3} style={{ margin: 0 }}>
            {formatMoney(orderTotal)} / {formatMoney(orderDealerTotal)} (дилер)
          </Typography.Title>
        </Space>
      </div>
      {/* Информационный блок и панель выгрузки — на одном уровне, во всю ширину страницы (см. правка
          пользователя и макет, «Сводка и действия»): слева — пояснение (flex: 1), справа — фиксированная
          по ширине панель выгрузки, обе одинаковой высоты (alignItems: stretch). */}
      <div style={{ display: 'flex', gap: 16, alignItems: 'stretch' }}>
        <Alert
          style={{ flex: 1 }}
          type="info"
          showIcon
          message={<Typography.Text strong>Конфигурация сохранена в заказе</Typography.Text>}
          description={
            <>
              Откройте позицию, чтобы проверить полный состав и параметры.
              <br />
              При изменении конфигурации цены будут пересчитаны.
            </>
          }
        />
        <div
          style={{
            width: 320,
            flexShrink: 0,
            border: '1px solid #d9d9d9',
            borderRadius: 8,
            padding: 16,
            display: 'flex',
            flexDirection: 'column',
            gap: 14,
          }}
        >
          <Typography.Text strong>Выгрузка заказа</Typography.Text>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <Typography.Text type="secondary">Формат файла</Typography.Text>
            <Typography.Text strong>Excel (.xlsx)</Typography.Text>
          </div>
          {exportError && <Alert type="error" message={exportError} showIcon />}
          <Button type="primary" block icon={<DownloadOutlined />} loading={exportLoading} onClick={handleExportOrder}>
            Выгрузить заказ
          </Button>
        </div>
      </div>
    </div>
  )
}

export default CartScreen
