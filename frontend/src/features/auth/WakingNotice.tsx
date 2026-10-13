import { useEffect, useState } from 'react'
import { Alert } from '../../components/ui/Alert'

export function WakingNotice() {
  const [seconds, setSeconds] = useState(0)

  useEffect(() => {
    const timer = setInterval(() => setSeconds((value) => value + 1), 1_000)
    return () => clearInterval(timer)
  }, [])

  return (
    <Alert tone="info" title="Acordando o servidor">
      A demo roda no plano gratuito do Render, que hiberna o servidor quando ninguém usa. Ele volta em
      até 1 minuto{seconds > 0 && ` (${seconds} s)`}. Você já pode preencher os dados.
    </Alert>
  )
}
