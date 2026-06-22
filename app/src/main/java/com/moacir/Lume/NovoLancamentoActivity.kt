package com.moacir.Lume

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.moacir.Lume.database.AppDatabase
import com.moacir.Lume.databinding.ActivityNovoLancamentoBinding
import com.moacir.Lume.model.Agenda
import com.moacir.Lume.model.Categoria
import com.moacir.Lume.model.Lancamento
import com.moacir.Lume.model.TipoLancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class NovoLancamentoActivity : AppCompatActivity() {

    private val binding by lazy { ActivityNovoLancamentoBinding.inflate(layoutInflater) }
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var categorias: List<Categoria> = emptyList()
    private var dataSelecionadaMillis: Long = System.currentTimeMillis()
    private var isAgenda: Boolean = false
    private var lancamentoParaEditar: Lancamento? = null
    private var agendaParaEditar: Agenda? = null

    // Trava para evitar que o Spinner mude a descrição durante o preenchimento automático
    private var bloqueioManual = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        isAgenda = intent.getBooleanExtra("IS_AGENDA", false)

        configurarInterfaceInicial()
        carregarDadosIniciais()
    }

    private fun configurarInterfaceInicial() {
        atualizarTextoData(Calendar.getInstance())
        configurarCampoValor()
        configurarCampoData()
        configurarMudancaDeTipo()
        configurarBotaoSalvar()
        configurarCheckRepetir()
    }

    private fun configurarCheckRepetir() {
        binding.chkRepetir.setOnCheckedChangeListener { _, isChecked ->
            val visibilidade = if (isChecked) View.VISIBLE else View.GONE
            binding.edtQtdMeses.visibility = visibilidade
            binding.txtMesesLabel.visibility = visibilidade
            if (!isChecked) binding.edtQtdMeses.setText("")
        }
    }

    private fun carregarDadosIniciais() {
        lifecycleScope.launch {
            val idRecebido = intent.getIntExtra("LANCAMENTO_ID", -1)

            // 1. Configura visibilidade do container de repetição
            binding.containerRepetir.visibility =
                if (isAgenda && idRecebido == -1) View.VISIBLE else View.GONE

            // 2. Busca categorias no banco
            categorias = withContext(Dispatchers.IO) { db.orcamentoDao().listarCategorias() }

            // 3. Configura Spinner
            val adapter = ArrayAdapter(
                this@NovoLancamentoActivity,
                android.R.layout.simple_spinner_dropdown_item,
                categorias.map { it.nome }
            )
            binding.spinnerCategorias.adapter = adapter

            // 4. Listener do Spinner com lógica de atualização de descrição
            binding.spinnerCategorias.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        p: AdapterView<*>?,
                        v: View?,
                        position: Int,
                        id: Long
                    ) {
                        if (!bloqueioManual && binding.rbDespesa.isChecked) {
                            val novaCatNome = categorias[position].nome
                            val descAtual = binding.edtDescricao.text.toString().trim()

                            // Lista de nomes de categorias para comparar
                            val nomesDasCategorias = categorias.map { it.nome.lowercase() }

                            // ATUALIZA SE: a descrição estiver vazia OU se a descrição atual for o nome de alguma categoria
                            if (descAtual.isEmpty() || nomesDasCategorias.contains(descAtual.lowercase())) {
                                binding.edtDescricao.setText(novaCatNome)
                            }
                        }
                    }

                    override fun onNothingSelected(p: AdapterView<*>?) {}
                }

            // 5. Se for edição, carrega os dados
            if (idRecebido != -1) {
                bloqueioManual = true // Ativa a trava antes de começar a carregar
                binding.txtTituloTela.text = if (isAgenda) "Editar Agenda" else "Editar Lançamento"
                carregarParaEdicao(idRecebido)

                // Libera a trava apenas depois que tudo foi preenchido
                bloqueioManual = false
            } else {
                binding.txtTituloTela.text = if (isAgenda) "Nova Agenda" else "Novo Lançamento"
                atualizarVisibilidadeCategoria(binding.rbDespesa.isChecked)
            }
        }
    }

    private suspend fun carregarParaEdicao(id: Int) {
        withContext(Dispatchers.IO) {
            if (isAgenda) {
                db.agendaDao().buscarPorId(id)?.let {
                    agendaParaEditar = it
                    withContext(Dispatchers.Main) {
                        preencherCampos(it.descricao, it.valor, it.tipo, it.data, it.categoriaID)
                    }
                }
            } else {
                db.orcamentoDao().buscarPorId(id)?.let {
                    lancamentoParaEditar = it
                    withContext(Dispatchers.Main) {
                        preencherCampos(it.descricao, it.valor, it.tipo, it.data, it.categoriaID)
                    }
                }
            }
        }
    }

    private fun preencherCampos(
        desc: String,
        valor: Double,
        tipo: TipoLancamento,
        data: Long,
        catId: Int
    ) {
        with(binding) {
            val valorFormatado = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(valor)
            edtValor.setText(valorFormatado)
            edtDescricao.setText(desc)

            if (tipo == TipoLancamento.RECEITA) rbReceita.isChecked =
                true else rbDespesa.isChecked = true
            atualizarVisibilidadeCategoria(tipo == TipoLancamento.DESPESA)

            val cal = Calendar.getInstance().apply { timeInMillis = data }
            atualizarTextoData(cal)

            val posicao = categorias.indexOfFirst { it.id == catId }
            if (posicao != -1) {
                // Ao setar a seleção aqui, o bloqueioManual impede que a descrição seja alterada
                spinnerCategorias.setSelection(posicao)
            }
        }
    }

    private fun configurarBotaoSalvar() {
        binding.btnSalvarLancamento.setOnClickListener {
            val descricao = binding.edtDescricao.text.toString()
            val cleanValor = binding.edtValor.text.toString().replace(Regex("[^0-9]"), "")

            if (descricao.isBlank() || cleanValor.isBlank()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val valorFinal = cleanValor.toDouble() / 100
            val tipo =
                if (binding.rbReceita.isChecked) TipoLancamento.RECEITA else TipoLancamento.DESPESA
            val catId = if (tipo == TipoLancamento.DESPESA) {
                categorias[binding.spinnerCategorias.selectedItemPosition].id
            } else {
                categorias.find { it.nome.equals("Receita", true) }?.id ?: 1
            }

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (isAgenda) salvarAgenda(descricao, valorFinal, tipo, catId)
                    else salvarLancamento(descricao, valorFinal, tipo, catId)
                }
                Toast.makeText(this@NovoLancamentoActivity, "Salvo!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private suspend fun salvarAgenda(
        desc: String,
        valor: Double,
        tipo: TipoLancamento,
        catId: Int
    ) {
        if (agendaParaEditar != null) {
            db.agendaDao().upsertAgenda(
                agendaParaEditar!!.copy(
                    descricao = desc,
                    valor = valor,
                    data = dataSelecionadaMillis,
                    categoriaID = catId,
                    tipo = tipo
                )
            )
        } else {
            val numRepeticoes =
                binding.edtQtdMeses.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
            val cal = Calendar.getInstance().apply { timeInMillis = dataSelecionadaMillis }
            for (i in 0 until numRepeticoes) {
                val descFinal = if (numRepeticoes > 1) "$desc (${i + 1}/$numRepeticoes)" else desc
                db.agendaDao().upsertAgenda(
                    Agenda(
                        id = 0,
                        descricao = descFinal,
                        valor = valor,
                        data = cal.timeInMillis,
                        categoriaID = catId,
                        tipo = tipo
                    )
                )
                cal.add(Calendar.MONTH, 1)
            }
        }
    }

    private suspend fun salvarLancamento(
        desc: String,
        valor: Double,
        tipo: TipoLancamento,
        catId: Int
    ) {
        val lancamento = lancamentoParaEditar?.copy(
            descricao = desc,
            valor = valor,
            data = dataSelecionadaMillis,
            categoriaID = catId,
            tipo = tipo
        ) ?: Lancamento(
            id = 0,
            descricao = desc,
            valor = valor,
            data = dataSelecionadaMillis,
            categoriaID = catId,
            tipo = tipo
        )
        db.orcamentoDao().upsertLancamento(lancamento)
    }

    private fun configurarCampoData() {
        binding.edtData.setOnClickListener {
            // Criamos o seletor garantindo que ele comece na data que já está na tela
            val builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecione a Data")
                .setSelection(dataSelecionadaMillis)

            val picker = builder.build()
            picker.addOnPositiveButtonClickListener { selection ->
                // O MaterialDatePicker retorna a data em UTC meia-noite.
                // Para evitar que o fuso horário do Brasil (UTC-3) jogue a data para o dia anterior,
                // precisamos compensar o offset.
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.timeInMillis = selection

                // Agora convertemos para o calendário local sem mudar o dia
                val localCalendar = Calendar.getInstance()
                localCalendar.set(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )

                atualizarTextoData(localCalendar)
            }
            picker.show(supportFragmentManager, "MATERIAL_DATE_PICKER")
        }
    }

    private fun atualizarTextoData(calendario: Calendar) {
        // Zeramos as horas, minutos e segundos para garantir que a
        // data selecionada represente o início do dia (00:00:00)
        calendario.set(Calendar.HOUR_OF_DAY, 0)
        calendario.set(Calendar.MINUTE, 0)
        calendario.set(Calendar.SECOND, 0)
        calendario.set(Calendar.MILLISECOND, 0)

        val formato = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        binding.edtData.setText(formato.format(calendario.time))
        dataSelecionadaMillis = calendario.timeInMillis
    }

    private fun configurarMudancaDeTipo() {
        binding.radioGroupTipo.setOnCheckedChangeListener { _, checkedId ->
            val isDespesa = checkedId == R.id.rbDespesa
            atualizarVisibilidadeCategoria(isDespesa)

            if (!isDespesa) {
                binding.edtDescricao.setText("Receita")
            } else {
                val pos = binding.spinnerCategorias.selectedItemPosition
                if (pos != -1) {
                    val descAtual = binding.edtDescricao.text.toString()
                    // Ao mudar o rádio de Receita para Despesa, ele recupera a categoria
                    if (descAtual == "Receita" || descAtual.isEmpty()) {
                        binding.edtDescricao.setText(categorias[pos].nome)
                    }
                }
            }
        }
    }

    private fun atualizarVisibilidadeCategoria(isDespesa: Boolean) {
        val visibilidade = if (isDespesa) View.VISIBLE else View.GONE
        binding.lblCategoria.visibility = visibilidade
        binding.spinnerCategorias.visibility = visibilidade
    }

    private fun configurarCampoValor() {
        binding.edtValor.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    binding.edtValor.removeTextChangedListener(this)
                    val cleanString = s.toString().replace(Regex("[^0-9]"), "")
                    if (cleanString.isNotEmpty()) {
                        val formatted = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
                            .format(cleanString.toDouble() / 100)
                        current = formatted
                        binding.edtValor.setText(formatted)
                        binding.edtValor.setSelection(formatted.length)
                    }
                    binding.edtValor.addTextChangedListener(this)
                }
            }
        })
    }
}